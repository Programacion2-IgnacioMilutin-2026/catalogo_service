package catalogo.synchronization.application.usecases;

import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.SyncErrorType;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.in.RecordSyncFailureUseCase;
import catalogo.catalog.domain.ports.in.ReplaceCatalogUseCase;
import catalogo.catedra.CatalogSnapshots;
import catalogo.catedra.domain.model.CatalogSnapshotResult;
import catalogo.catedra.domain.ports.in.GetCatalogSnapshotUseCase;
import catalogo.synchronization.application.support.SynchronizationLock;
import catalogo.synchronization.domain.model.FullSyncResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// El proceso de la sincronización completa, con puertos falsos de catedra y de catalog.
@ExtendWith(OutputCaptureExtension.class)
class SynchronizeFullCatalogUseCaseImplTest {

    private static final CatalogSnapshotResult OBTAINED = new CatalogSnapshotResult.Obtained(CatalogSnapshots.valid());

    private final SynchronizationLock lock = new SynchronizationLock();
    private final AtomicInteger snapshotRequests = new AtomicInteger();
    private final List<Replacement> replacements = new ArrayList<>();
    private final List<SyncErrorType> recordedFailures = new ArrayList<>();
    private Supplier<CatalogSnapshotResult> catedra = () -> OBTAINED;
    private RuntimeException replaceFailure;
    private RuntimeException recordFailure;

    @Test
    void anObtainedSnapshotReplacesTheCopyWithItsVersion() {
        FullSyncResult result = useCase().synchronizeFullCatalog();

        assertThat(result).isEqualTo(new FullSyncResult.Applied(CatalogSnapshots.VERSION));
        assertThat(replacements).singleElement().satisfies(replacement ->
                assertThat(replacement.version()).isEqualTo(CatalogSnapshots.VERSION));
        assertThat(recordedFailures).isEmpty();
    }

    @Test
    void theTranslationToTheLocalCopyKeepsEveryFieldAndResolvesTheReferences() {
        useCase().synchronizeFullCatalog();

        Replacement replacement = replacements.getFirst();
        ProfessionalCategory kinesiology = category(501, "Kinesiología",
                "Rehabilitación física y tratamiento de lesiones musculares");
        ProfessionalCategory nutrition = category(502, "Nutrición", "Planes de alimentación y control de peso");
        Professional valentina = professional(601, "Valentina", "Ibáñez", true, kinesiology);
        Professional tomas = professional(602, "Tomás", "Ferreyra", false, kinesiology);
        Professional lucia = professional(603, "Lucía", "Núñez", true, nutrition);
        assertThat(replacement.categories()).usingRecursiveFieldByFieldElementComparator()
                .containsExactly(kinesiology, nutrition);
        assertThat(replacement.professionals()).usingRecursiveFieldByFieldElementComparator()
                .containsExactly(valentina, tomas, lucia);
        assertThat(replacement.weeklySchedules()).usingRecursiveFieldByFieldElementComparator().containsExactly(
                schedule(701, DayOfWeek.MONDAY, "08:00", "12:00", 30, true, valentina),
                schedule(702, DayOfWeek.THURSDAY, "14:00", "18:00", 30, true, valentina),
                schedule(703, DayOfWeek.TUESDAY, "09:00", "13:00", 20, false, tomas),
                schedule(704, DayOfWeek.FRIDAY, "10:00", "14:00", 20, true, lucia));
        replacement.categories().forEach(c -> assertThat(c).hasNoNullFieldsOrProperties());
        replacement.professionals().forEach(p -> assertThat(p).hasNoNullFieldsOrProperties());
        replacement.weeklySchedules().forEach(s -> assertThat(s).hasNoNullFieldsOrProperties());
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> failures() {
        return Stream.of(
                arguments(new CatalogSnapshotResult.Unavailable("not reachable"), SyncErrorType.SOURCE_UNAVAILABLE),
                arguments(new CatalogSnapshotResult.Rejected("HTTP 403"), SyncErrorType.INTEGRATION_REJECTED),
                arguments(new CatalogSnapshotResult.Invalid("missing lastName"), SyncErrorType.INVALID_DATA));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void eachFailureFromTheCatedraIsRecordedWithItsTypeAndTheCopyIsNotTouched(
            CatalogSnapshotResult snapshotResult, SyncErrorType expected) {
        catedra = () -> snapshotResult;

        FullSyncResult result = useCase().synchronizeFullCatalog();

        assertThat(result).isInstanceOf(FullSyncResult.Failed.class);
        assertThat(((FullSyncResult.Failed) result).reason()).startsWith(expected.name());
        assertThat(recordedFailures).containsExactly(expected);
        assertThat(replacements).isEmpty();
    }

    @Test
    void aFailingReplacementIsAPersistenceFailure() {
        replaceFailure = new IllegalStateException("database down");

        FullSyncResult result = useCase().synchronizeFullCatalog();

        assertThat(result).isEqualTo(new FullSyncResult.Failed(
                "PERSISTENCE_FAILURE: The local copy could not be replaced: IllegalStateException"));
        assertThat(recordedFailures).containsExactly(SyncErrorType.PERSISTENCE_FAILURE);
    }

    @Test
    void whenTheFailureCannotBeRecordedEitherItStaysInTheLog(CapturedOutput output) {
        replaceFailure = new IllegalStateException("database down");
        recordFailure = new IllegalStateException("database still down");

        FullSyncResult result = useCase().synchronizeFullCatalog();

        assertThat(result).isInstanceOf(FullSyncResult.Failed.class);
        assertThat(output.getOut())
                .contains("Full catalog synchronization failed with PERSISTENCE_FAILURE")
                .contains("could not be recorded: IllegalStateException");
    }

    @Test
    @Timeout(10)
    void aSecondRequestWhileOneIsInProgressIsDiscarded() throws Exception {
        CountDownLatch firstIsInside = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        catedra = () -> {
            firstIsInside.countDown();
            await(secondFinished);
            return OBTAINED;
        };
        SynchronizeFullCatalogUseCaseImpl useCase = useCase();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<FullSyncResult> first = executor.submit(useCase::synchronizeFullCatalog);
            await(firstIsInside);

            FullSyncResult second = executor.submit(useCase::synchronizeFullCatalog).get(5, TimeUnit.SECONDS);
            secondFinished.countDown();

            assertThat(second).isEqualTo(new FullSyncResult.Discarded());
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(new FullSyncResult.Applied(CatalogSnapshots.VERSION));
        }
        assertThat(snapshotRequests).hasValue(1);
        assertThat(replacements).hasSize(1);
        assertThat(recordedFailures).isEmpty();
    }

    @Test
    void aRequestFromInsideASynchronizationInProgressRuns() {
        assertThat(lock.tryAcquire()).isTrue();
        try {
            assertThat(useCase().synchronizeFullCatalog())
                    .isEqualTo(new FullSyncResult.Applied(CatalogSnapshots.VERSION));
        } finally {
            lock.release();
        }
    }

    @Test
    void theLockIsReleasedAfterEachRunEvenWithAnUnexpectedError() {
        catedra = () -> {
            throw new IllegalStateException("boom");
        };
        assertThatThrownBy(() -> useCase().synchronizeFullCatalog()).isInstanceOf(IllegalStateException.class);
        catedra = () -> OBTAINED;

        assertThat(useCase().synchronizeFullCatalog()).isInstanceOf(FullSyncResult.Applied.class);
        assertThat(lock.tryAcquire()).isTrue();
        lock.release();
    }

    @Test
    void theLogShowsTheAppliedVersionAndTheCounts(CapturedOutput output) {
        useCase().synchronizeFullCatalog();

        assertThat(output.getOut()).contains(
                "Full catalog synchronization applied version 57: 2 categories, 3 professionals, 4 weekly schedules");
    }

    private SynchronizeFullCatalogUseCaseImpl useCase() {
        GetCatalogSnapshotUseCase getSnapshot = () -> {
            snapshotRequests.incrementAndGet();
            return catedra.get();
        };
        ReplaceCatalogUseCase replace = (version, categories, professionals, schedules) -> {
            if (replaceFailure != null) {
                throw replaceFailure;
            }
            replacements.add(new Replacement(version, categories, professionals, schedules));
        };
        RecordSyncFailureUseCase record = errorType -> {
            if (recordFailure != null) {
                throw recordFailure;
            }
            recordedFailures.add(errorType);
        };
        return new SynchronizeFullCatalogUseCaseImpl(lock, getSnapshot, replace, record);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static ProfessionalCategory category(long id, String name, String description) {
        return ProfessionalCategory.builder().id(id).name(name).description(description).enabled(true).build();
    }

    private static Professional professional(long id, String firstName, String lastName, boolean enabled,
                                             ProfessionalCategory category) {
        return Professional.builder().id(id).firstName(firstName).lastName(lastName).enabled(enabled)
                .category(category).build();
    }

    private static WeeklySchedule schedule(long id, DayOfWeek day, String start, String end, int slotMinutes,
                                           boolean enabled, Professional professional) {
        return WeeklySchedule.builder().id(id).dayOfWeek(day).startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end)).slotDurationMinutes(slotMinutes).enabled(enabled)
                .professional(professional).build();
    }

    private record Replacement(long version, List<ProfessionalCategory> categories, List<Professional> professionals,
                               List<WeeklySchedule> weeklySchedules) {
    }
}
