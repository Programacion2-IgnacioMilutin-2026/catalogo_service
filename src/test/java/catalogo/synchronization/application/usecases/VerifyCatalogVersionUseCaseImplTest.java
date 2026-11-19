package catalogo.synchronization.application.usecases;

import catalogo.catalog.domain.model.ApplyCatalogVersionResult;
import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.SyncErrorType;
import catalogo.catalog.domain.model.SyncType;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.in.ApplyCatalogVersionUseCase;
import catalogo.catalog.domain.ports.in.RecordSyncFailureUseCase;
import catalogo.catalog.domain.ports.in.RecordUpToDateCheckUseCase;
import catalogo.catedra.CatalogSnapshots;
import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogMetadataResult;
import catalogo.catedra.domain.model.CatalogVersionChanges;
import catalogo.catedra.domain.model.CatalogVersionChangesResult;
import catalogo.catedra.domain.ports.in.GetCatalogVersionChangesUseCase;
import catalogo.synchronization.application.support.SynchronizationLock;
import catalogo.synchronization.domain.model.FullSyncResult;
import catalogo.synchronization.domain.model.VerificationPolicy;
import catalogo.synchronization.domain.model.VerificationResult;
import catalogo.synchronization.domain.ports.in.SynchronizeFullCatalogUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// La verificación de la versión del catálogo con puertos falsos: qué lee, qué aplica, qué pide y qué registra.
@ExtendWith(OutputCaptureExtension.class)
class VerifyCatalogVersionUseCaseImplTest {

    private final SynchronizationLock lock = new SynchronizationLock();
    private final CatalogSyncState state = new CatalogSyncState();
    private final Deque<CatalogMetadataResult> metadata = new ArrayDeque<>();
    private final Map<Long, CatalogVersionChangesResult> versions = new HashMap<>();
    private final List<Long> requestedVersions = new ArrayList<>();
    private final Deque<FullSyncResult> fullResults = new ArrayDeque<>();
    private final List<Applied> applied = new ArrayList<>();
    private final Map<Long, Deque<RuntimeException>> applyFailures = new HashMap<>();
    private final Map<Long, ApplyCatalogVersionResult.NotVerifiable> notVerifiable = new HashMap<>();
    private final List<SyncErrorType> recordedFailures = new ArrayList<>();
    private int metadataReads;
    private int fullSynchronizations;
    private int upToDateChecks;
    private int initialLoginWaits;
    private RuntimeException recordFailure;

    private record Applied(long version, List<ProfessionalCategory> categories, List<Professional> professionals,
                           List<WeeklySchedule> weeklySchedules) {
    }

    @Test
    void anUpToDateCatalogReadsNoVersionAndOnlyRecordsTheCheck(CapturedOutput output) {
        localVersion(59);
        metadata(59, 50);

        assertThat(useCase().verifyCatalogVersion()).isEqualTo(new VerificationResult.UpToDate(59));

        assertThat(requestedVersions).isEmpty();
        assertThat(upToDateChecks).isEqualTo(1);
        assertThat(recordedFailures).isEmpty();
        assertThat(fullSynchronizations).isZero();
        assertThat(output).contains("Catalog verification: up to date at version 59");
    }

    @Test
    void aCatalogBehindAppliesThePendingVersionsInAscendingOrder() {
        localVersion(56);
        metadata(59, 50);

        assertThat(useCase().verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(59));

        assertThat(requestedVersions).containsExactly(57L, 58L, 59L);
        assertThat(applied).extracting(Applied::version).containsExactly(57L, 58L, 59L);
        assertThat(state.getLocalVersion()).isEqualTo(59L);
        assertThat(fullSynchronizations).isZero();
    }

    @Test
    void eachVersionIsTranslatedWithReferencesOnlyById() {
        localVersion(58);
        metadata(59, 50);
        versions.put(59L, new CatalogVersionChangesResult.Obtained(new CatalogVersionChanges(59,
                List.of(CatalogSnapshots.categories().get(1)), List.of(CatalogSnapshots.professionals().getFirst()),
                List.of(CatalogSnapshots.weeklySchedules().get(1)))));

        useCase().verifyCatalogVersion();

        Applied version = applied.getFirst();
        assertThat(version.categories().getFirst())
                .extracting(ProfessionalCategory::getId, ProfessionalCategory::getName, ProfessionalCategory::isEnabled)
                .containsExactly(502L, "Nutrición", true);
        Professional professional = version.professionals().getFirst();
        assertThat(professional)
                .extracting(Professional::getId, Professional::getFirstName, Professional::getLastName)
                .containsExactly(601L, "Valentina", "Ibáñez");
        assertThat(professional.getCategory().getId()).isEqualTo(501L);
        assertThat(professional.getCategory().getName()).isNull();
        WeeklySchedule schedule = version.weeklySchedules().getFirst();
        assertThat(schedule.getId()).isEqualTo(702L);
        assertThat(schedule.getSlotDurationMinutes()).isEqualTo(30);
        assertThat(schedule.getProfessional().getId()).isEqualTo(601L);
        assertThat(schedule.getProfessional().getFirstName()).isNull();
    }

    @Test
    void aLocalVersionEqualToTheOldestAvailableContinuesIncrementally() {
        localVersion(50);
        metadata(51, 50);

        assertThat(useCase().verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(51));

        assertThat(fullSynchronizations).isZero();
        assertThat(requestedVersions).containsExactly(51L);
    }

    @Test
    void aLocalVersionOlderThanTheHistoryRequestsTheFullSynchronizationAndThenAppliesWhatCameAfter() {
        localVersion(40);
        metadata(59, 50);
        fullAppliedAt(60);
        metadata(61, 50);

        assertThat(useCase().verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(61));

        assertThat(fullSynchronizations).isEqualTo(1);
        assertThat(requestedVersions).containsExactly(61L);
    }

    @Test
    void aLocalVersionAheadOfRedisRequestsTheFullSynchronization() {
        localVersion(70);
        metadata(59, 50);
        fullAppliedAt(59);
        metadata(59, 50);

        assertThat(useCase().verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(59));

        assertThat(fullSynchronizations).isEqualTo(1);
        assertThat(requestedVersions).isEmpty();
    }

    @Test
    void anotherSynchronizationInProgressSkipsTheVerification(CapturedOutput output) throws InterruptedException {
        localVersion(56);
        metadata(59, 50);
        CountDownLatch taken = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread other = Thread.ofVirtual().start(() -> {
            lock.tryAcquire();
            taken.countDown();
            await(release);
            lock.release();
        });
        await(taken);

        VerificationResult result = useCase().verifyCatalogVersion();
        release.countDown();
        other.join();

        assertThat(result).isEqualTo(new VerificationResult.Skipped());
        assertThat(metadataReads).isZero();
        assertThat(recordedFailures).isEmpty();
        assertThat(upToDateChecks).isZero();
        assertThat(output).contains("Catalog verification skipped: another synchronization is in progress");
    }

    @Test
    void withoutALocalVersionTheFullSynchronizationIsRequestedBeforeReadingRedis() {
        fullAppliedAt(59);
        metadata(59, 50);

        assertThat(useCase().verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(59));

        assertThat(fullSynchronizations).isEqualTo(1);
        assertThat(metadataReads).isEqualTo(1);
    }

    @Test
    void withoutALocalVersionAndRedisDownTheSnapshotIsLoadedAndTheFailureRecorded() {
        fullAppliedAt(59);
        metadata.add(new CatalogMetadataResult.Unavailable("Redis did not answer in time"));

        VerificationResult result = useCase().verifyCatalogVersion();

        assertThat(result).isInstanceOf(VerificationResult.Failed.class);
        assertThat(((VerificationResult.Failed) result).reason()).startsWith("SOURCE_UNAVAILABLE");
        assertThat(state.getLocalVersion()).isEqualTo(59L);
        assertThat(recordedFailures).containsExactly(SyncErrorType.SOURCE_UNAVAILABLE);
    }

    @Test
    void aFailedFullSynchronizationEndsWithoutRecordingAnotherError() {
        fullResults.add(new FullSyncResult.Failed("SOURCE_UNAVAILABLE: Central API answered HTTP 503"));

        assertThat(useCase().verifyCatalogVersion())
                .isEqualTo(new VerificationResult.Failed("SOURCE_UNAVAILABLE: Central API answered HTTP 503"));

        assertThat(recordedFailures).isEmpty();
        assertThat(metadataReads).isZero();
    }

    @Test
    void anUnavailableOrRejectingRedisIsRecordedWithItsType() {
        localVersion(56);
        metadata.add(new CatalogMetadataResult.Unavailable("Redis did not answer in time"));
        assertThat(((VerificationResult.Failed) useCase().verifyCatalogVersion()).reason())
                .startsWith("SOURCE_UNAVAILABLE");

        metadata.clear();
        metadata.add(new CatalogMetadataResult.Rejected("Redis rejected the renewed session: NOPERM"));
        assertThat(((VerificationResult.Failed) useCase().verifyCatalogVersion()).reason())
                .startsWith("INTEGRATION_REJECTED");

        assertThat(recordedFailures).containsExactly(SyncErrorType.SOURCE_UNAVAILABLE, SyncErrorType.INTEGRATION_REJECTED);
    }

    @Test
    void invalidMetadataRequestsTheFullSynchronizationAndAPersistentProblemIsInvalidData() {
        localVersion(56);
        metadata.add(new CatalogMetadataResult.Invalid("invalid catalog metadata: [missing currentVersion]"));
        fullAppliedAt(59);

        VerificationResult result = useCase().verifyCatalogVersion();

        assertThat(fullSynchronizations).isEqualTo(1);
        assertThat(((VerificationResult.Failed) result).reason())
                .startsWith("INVALID_DATA: The discontinuity persists after the full synchronization");
        assertThat(recordedFailures).containsExactly(SyncErrorType.INVALID_DATA);
    }

    @Test
    void anInvalidVersionRequestsTheFullSynchronizationOnce() {
        localVersion(56);
        metadata(59, 50);
        versions.put(57L, new CatalogVersionChangesResult.Invalid("the changes of version 57 do not exist"));
        fullAppliedAt(59);
        metadata(59, 50);

        assertThat(useCase().verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(59));

        assertThat(fullSynchronizations).isEqualTo(1);
        assertThat(applied).isEmpty();
        assertThat(recordedFailures).isEmpty();
    }

    @Test
    void aBrokenReferenceRequestsTheFullSynchronization() {
        localVersion(58);
        metadata(59, 50);
        notVerifiable.put(59L, new ApplyCatalogVersionResult.NotVerifiable(
                List.of("professional 601 references the missing professional category 999")));
        fullAppliedAt(59);
        metadata(59, 50);

        assertThat(useCase().verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(59));

        assertThat(fullSynchronizations).isEqualTo(1);
        assertThat(applied).isEmpty();
    }

    @Test
    void aPersistenceFailureIsRetriedAndTheVersionAppliedWhenTheDatabaseRecovers(CapturedOutput output) {
        localVersion(56);
        metadata(59, 50);
        applyFails(58, new IllegalStateException("down"), new IllegalStateException("down"));

        assertThat(useCase().verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(59));

        assertThat(applied).extracting(Applied::version).containsExactly(57L, 58L, 59L);
        assertThat(output).contains("Catalog version 58 attempt 1/3 could not be applied: IllegalStateException");
    }

    @Test
    void aPersistenceFailureThatPersistsKeepsTheVersionsAlreadyAppliedAndIsRecorded() {
        localVersion(56);
        metadata(59, 50);
        applyFails(58, new IllegalStateException("down"), new IllegalStateException("down"),
                new IllegalStateException("down"));

        VerificationResult result = useCase().verifyCatalogVersion();

        assertThat(((VerificationResult.Failed) result).reason()).startsWith("PERSISTENCE_FAILURE");
        assertThat(applied).extracting(Applied::version).containsExactly(57L);
        assertThat(state.getLocalVersion()).isEqualTo(57L);
        assertThat(requestedVersions).containsExactly(57L, 58L);
        assertThat(recordedFailures).containsExactly(SyncErrorType.PERSISTENCE_FAILURE);
    }

    @Test
    void redisFailingInTheMiddleOfTheVersionsKeepsTheOnesAlreadyApplied() {
        localVersion(56);
        metadata(59, 50);
        versions.put(58L, new CatalogVersionChangesResult.Unavailable("Redis did not answer in time"));

        VerificationResult result = useCase().verifyCatalogVersion();

        assertThat(((VerificationResult.Failed) result).reason()).startsWith("SOURCE_UNAVAILABLE");
        assertThat(state.getLocalVersion()).isEqualTo(57L);
        assertThat(recordedFailures).containsExactly(SyncErrorType.SOURCE_UNAVAILABLE);
    }

    @Test
    void aFailureThatCannotBeRecordedIsLoggedAndTheVerificationEnds(CapturedOutput output) {
        localVersion(56);
        metadata.add(new CatalogMetadataResult.Unavailable("Redis did not answer in time"));
        recordFailure = new IllegalStateException("database down");

        VerificationResult result = useCase().verifyCatalogVersion();

        assertThat(result).isInstanceOf(VerificationResult.Failed.class);
        assertThat(output).contains("The failed catalog verification could not be recorded: IllegalStateException");
    }

    @Test
    void theVerificationWaitsForTheInitialLoginBeforeTakingTheLock() {
        localVersion(59);
        metadata(59, 50);

        useCase().verifyCatalogVersion();

        assertThat(initialLoginWaits).isEqualTo(1);
    }

    private VerifyCatalogVersionUseCaseImpl useCase() {
        SynchronizeFullCatalogUseCase full = () -> {
            fullSynchronizations++;
            FullSyncResult result = fullResults.isEmpty()
                    ? new FullSyncResult.Failed("SOURCE_UNAVAILABLE: not configured in the test")
                    : fullResults.poll();
            if (result instanceof FullSyncResult.Applied appliedFull) {
                state.recordSuccess(SyncType.FULL, appliedFull.snapshotVersion(), Instant.now());
            }
            return result;
        };
        GetCatalogVersionChangesUseCase versionChanges = version -> {
            requestedVersions.add(version);
            return versions.getOrDefault(version, new CatalogVersionChangesResult.Obtained(
                    new CatalogVersionChanges(version, List.of(), List.of(), List.of())));
        };
        ApplyCatalogVersionUseCase apply = (version, categories, professionals, schedules) -> {
            Deque<RuntimeException> failures = applyFailures.get(version);
            if (failures != null && !failures.isEmpty()) {
                throw failures.poll();
            }
            if (notVerifiable.containsKey(version)) {
                return notVerifiable.get(version);
            }
            applied.add(new Applied(version, categories, professionals, schedules));
            state.recordSuccess(SyncType.INCREMENTAL, version, Instant.now());
            return new ApplyCatalogVersionResult.Applied(version);
        };
        RecordSyncFailureUseCase record = errorType -> {
            if (recordFailure != null) {
                throw recordFailure;
            }
            recordedFailures.add(errorType);
        };
        RecordUpToDateCheckUseCase upToDate = () -> upToDateChecks++;
        return new VerifyCatalogVersionUseCaseImpl(lock,
                () -> {
                    initialLoginWaits++;
                    return Optional.empty();
                },
                () -> state, full,
                () -> {
                    metadataReads++;
                    return metadata.size() > 1 ? metadata.poll() : metadata.peek();
                },
                versionChanges, apply, record, upToDate,
                new VerificationPolicy(3, Duration.ofMillis(1), 2, Duration.ofMillis(5)));
    }

    private void localVersion(long version) {
        state.recordSuccess(SyncType.FULL, version, Instant.parse("2026-11-18T12:00:00Z"));
    }

    private void metadata(long current, long oldest) {
        metadata.add(new CatalogMetadataResult.Obtained(new CatalogMetadata(current, oldest)));
    }

    private void fullAppliedAt(long version) {
        fullResults.add(new FullSyncResult.Applied(version));
    }

    private void applyFails(long version, RuntimeException... failures) {
        applyFailures.put(version, new ArrayDeque<>(List.of(failures)));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
