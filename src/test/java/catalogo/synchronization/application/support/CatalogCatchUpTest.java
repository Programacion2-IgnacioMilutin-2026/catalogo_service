package catalogo.synchronization.application.support;

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
import catalogo.synchronization.domain.model.CatchUpResult;
import catalogo.synchronization.domain.model.FullSyncResult;
import catalogo.synchronization.domain.model.VerificationPolicy;
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

import static org.assertj.core.api.Assertions.assertThat;

// Ponerse al día con puertos falsos.
@ExtendWith(OutputCaptureExtension.class)
class CatalogCatchUpTest {

    private static final String NAME = "Test catch-up";

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
    private RuntimeException recordFailure;

    private record Applied(long version, List<ProfessionalCategory> categories, List<Professional> professionals,
                           List<WeeklySchedule> weeklySchedules) {
    }

    @Test
    void anUpToDateCatalogReadsNoVersionAndOnlyRecordsTheCheck(CapturedOutput output) {
        localVersion(59);
        metadata(59, 50);

        assertThat(catchUp()).isEqualTo(new CatchUpResult.UpToDate(59));

        assertThat(requestedVersions).isEmpty();
        assertThat(upToDateChecks).isEqualTo(1);
        assertThat(recordedFailures).isEmpty();
        assertThat(fullSynchronizations).isZero();
        assertThat(output).contains("Test catch-up: up to date at version 59");
    }

    @Test
    void aCatalogBehindAppliesThePendingVersionsInAscendingOrder() {
        localVersion(56);
        metadata(59, 50);

        assertThat(catchUp()).isEqualTo(new CatchUpResult.Updated(59));

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

        catchUp();

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

        assertThat(catchUp()).isEqualTo(new CatchUpResult.Updated(51));

        assertThat(fullSynchronizations).isZero();
        assertThat(requestedVersions).containsExactly(51L);
    }

    @Test
    void aLocalVersionOlderThanTheHistoryRequestsTheFullSynchronizationAndThenAppliesWhatCameAfter() {
        localVersion(40);
        metadata(59, 50);
        fullAppliedAt(60);
        metadata(61, 50);

        assertThat(catchUp()).isEqualTo(new CatchUpResult.Updated(61));

        assertThat(fullSynchronizations).isEqualTo(1);
        assertThat(requestedVersions).containsExactly(61L);
    }

    @Test
    void aLocalVersionAheadOfRedisRequestsTheFullSynchronization() {
        localVersion(70);
        metadata(59, 50);
        fullAppliedAt(59);
        metadata(59, 50);

        assertThat(catchUp()).isEqualTo(new CatchUpResult.Updated(59));

        assertThat(fullSynchronizations).isEqualTo(1);
        assertThat(requestedVersions).isEmpty();
    }

    @Test
    void withoutALocalVersionTheFullSynchronizationIsRequestedBeforeReadingRedis() {
        fullAppliedAt(59);
        metadata(59, 50);

        assertThat(catchUp()).isEqualTo(new CatchUpResult.Updated(59));

        assertThat(fullSynchronizations).isEqualTo(1);
        assertThat(metadataReads).isEqualTo(1);
    }

    @Test
    void withoutALocalVersionAndRedisDownTheSnapshotIsLoadedAndTheFailureRecorded() {
        fullAppliedAt(59);
        metadata.add(new CatalogMetadataResult.Unavailable("Redis did not answer in time"));

        CatchUpResult result = catchUp();

        assertThat(result).isInstanceOf(CatchUpResult.Failed.class);
        assertThat(((CatchUpResult.Failed) result).reason()).startsWith("SOURCE_UNAVAILABLE");
        assertThat(state.getLocalVersion()).isEqualTo(59L);
        assertThat(recordedFailures).containsExactly(SyncErrorType.SOURCE_UNAVAILABLE);
    }

    @Test
    void aFailedFullSynchronizationEndsWithoutRecordingAnotherError() {
        fullResults.add(new FullSyncResult.Failed("SOURCE_UNAVAILABLE: Central API answered HTTP 503"));

        assertThat(catchUp())
                .isEqualTo(new CatchUpResult.Failed("SOURCE_UNAVAILABLE: Central API answered HTTP 503"));

        assertThat(recordedFailures).isEmpty();
        assertThat(metadataReads).isZero();
    }

    @Test
    void aDiscardedFullSynchronizationIsReportedAsAFailureWithoutRecordingAnother(CapturedOutput output) {
        fullResults.add(new FullSyncResult.Discarded());

        assertThat(catchUp()).isEqualTo(new CatchUpResult.Failed("The full synchronization was discarded"));

        assertThat(recordedFailures).isEmpty();
        assertThat(metadataReads).isZero();
        assertThat(output).contains("Test catch-up: the full synchronization was discarded although the lock was held");
    }

    @Test
    void anUnavailableOrRejectingRedisIsRecordedWithItsType() {
        localVersion(56);
        metadata.add(new CatalogMetadataResult.Unavailable("Redis did not answer in time"));
        assertThat(((CatchUpResult.Failed) catchUp()).reason()).startsWith("SOURCE_UNAVAILABLE");

        metadata.clear();
        metadata.add(new CatalogMetadataResult.Rejected("Redis rejected the renewed session: NOPERM"));
        assertThat(((CatchUpResult.Failed) catchUp()).reason()).startsWith("INTEGRATION_REJECTED");

        assertThat(recordedFailures).containsExactly(SyncErrorType.SOURCE_UNAVAILABLE, SyncErrorType.INTEGRATION_REJECTED);
    }

    @Test
    void invalidMetadataRequestsTheFullSynchronizationAndAPersistentProblemIsInvalidData() {
        localVersion(56);
        metadata.add(new CatalogMetadataResult.Invalid("invalid catalog metadata: [missing currentVersion]"));
        fullAppliedAt(59);

        CatchUpResult result = catchUp();

        assertThat(fullSynchronizations).isEqualTo(1);
        assertThat(((CatchUpResult.Failed) result).reason())
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

        assertThat(catchUp()).isEqualTo(new CatchUpResult.Updated(59));

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

        assertThat(catchUp()).isEqualTo(new CatchUpResult.Updated(59));

        assertThat(fullSynchronizations).isEqualTo(1);
        assertThat(applied).isEmpty();
    }

    @Test
    void aPersistenceFailureIsRetriedAndTheVersionAppliedWhenTheDatabaseRecovers(CapturedOutput output) {
        localVersion(56);
        metadata(59, 50);
        applyFails(58, new IllegalStateException("down"), new IllegalStateException("down"));

        assertThat(catchUp()).isEqualTo(new CatchUpResult.Updated(59));

        assertThat(applied).extracting(Applied::version).containsExactly(57L, 58L, 59L);
        assertThat(output).contains("Catalog version 58 attempt 1/3 could not be applied: IllegalStateException");
    }

    @Test
    void aPersistenceFailureThatPersistsKeepsTheVersionsAlreadyAppliedAndIsRecorded() {
        localVersion(56);
        metadata(59, 50);
        applyFails(58, new IllegalStateException("down"), new IllegalStateException("down"),
                new IllegalStateException("down"));

        CatchUpResult result = catchUp();

        assertThat(((CatchUpResult.Failed) result).reason()).startsWith("PERSISTENCE_FAILURE");
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

        CatchUpResult result = catchUp();

        assertThat(((CatchUpResult.Failed) result).reason()).startsWith("SOURCE_UNAVAILABLE");
        assertThat(state.getLocalVersion()).isEqualTo(57L);
        assertThat(recordedFailures).containsExactly(SyncErrorType.SOURCE_UNAVAILABLE);
    }

    @Test
    void aFailureThatCannotBeRecordedIsLoggedAndTheCatchUpEnds(CapturedOutput output) {
        localVersion(56);
        metadata.add(new CatalogMetadataResult.Unavailable("Redis did not answer in time"));
        recordFailure = new IllegalStateException("database down");

        CatchUpResult result = catchUp();

        assertThat(result).isInstanceOf(CatchUpResult.Failed.class);
        assertThat(output).contains("Test catch-up: the failure could not be recorded: IllegalStateException");
    }

    private CatchUpResult catchUp() {
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
        return new CatalogCatchUp(() -> state, full,
                () -> {
                    metadataReads++;
                    return metadata.size() > 1 ? metadata.poll() : metadata.peek();
                },
                versionChanges, apply, record, upToDate,
                new VerificationPolicy(3, Duration.ofMillis(1), 2, Duration.ofMillis(5)))
                .catchUp(NAME);
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
}
