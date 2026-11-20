package catalogo.synchronization.application.usecases;

import catalogo.catalog.domain.model.ApplyCatalogVersionResult;
import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.model.SyncErrorType;
import catalogo.catalog.domain.model.SyncType;
import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogMetadataResult;
import catalogo.catedra.domain.model.CatalogVersionChanges;
import catalogo.catedra.domain.model.CatalogVersionChangesResult;
import catalogo.synchronization.application.support.CatalogCatchUp;
import catalogo.synchronization.application.support.SynchronizationLock;
import catalogo.synchronization.domain.model.FullSyncResult;
import catalogo.synchronization.domain.model.IncrementalSyncResult;
import catalogo.synchronization.domain.model.ProcessedEvent;
import catalogo.synchronization.domain.model.VerificationPolicy;
import catalogo.synchronization.domain.ports.out.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// El aviso de versión nueva con puertos falsos: cuándo se procesa, cuándo espera y qué registra. Lo que hace la pieza
// común se prueba en CatalogCatchUpTest.
@ExtendWith(OutputCaptureExtension.class)
class SynchronizeCatalogIncrementallyUseCaseImplTest {

    private static final String EVENT_ID = "81545656-d410-4dd1-8a06-18142a406205";
    private static final Instant NOW = Instant.parse("2026-11-19T15:18:51Z");

    private final SynchronizationLock lock = new SynchronizationLock();
    private final CatalogSyncState state = new CatalogSyncState();
    private final FakeProcessedEvents processedEvents = new FakeProcessedEvents();
    private final List<Long> requestedVersions = new ArrayList<>();
    private final List<SyncErrorType> recordedFailures = new ArrayList<>();
    private volatile CatalogMetadataResult metadata = metadata(59, 50);
    private FullSyncResult fullResult = new FullSyncResult.Failed("SOURCE_UNAVAILABLE: not configured in the test");
    private RuntimeException stateFailure;
    private int metadataReads;

    @Test
    void aNoticeThatAppliesVersionsIsRegisteredWithItsInstant(CapturedOutput output) throws InterruptedException {
        localVersion(57);

        assertThat(useCase().synchronizeCatalogIncrementally(EVENT_ID, 59))
                .isEqualTo(new IncrementalSyncResult.Updated(59));

        assertThat(requestedVersions).containsExactly(58L, 59L);
        assertThat(processedEvents.saved).containsKey(EVENT_ID);
        assertThat(processedEvents.saved.get(EVENT_ID).getProcessedAt()).isEqualTo(NOW);
        assertThat(lockIsFreeForAnotherThread()).isTrue();
        assertThat(output)
                .contains("Catalog update notice " + EVENT_ID + " received: newVersion 59")
                .contains("Catalog update notice " + EVENT_ID + " applied versions 58 to 59")
                .contains("Catalog update notice " + EVENT_ID + " finished: Updated[localVersion=59]");
    }

    @Test
    void aNoticeThatFindsTheCatalogUpToDateIsRegistered() {
        localVersion(59);

        assertThat(useCase().synchronizeCatalogIncrementally(EVENT_ID, 59))
                .isEqualTo(new IncrementalSyncResult.UpToDate(59));

        assertThat(requestedVersions).isEmpty();
        assertThat(processedEvents.saved).containsKey(EVENT_ID);
    }

    @Test
    void aNoticeWithoutALocalVersionLoadsTheSnapshotAndIsRegistered() {
        fullResult = new FullSyncResult.Applied(59);

        assertThat(useCase().synchronizeCatalogIncrementally(EVENT_ID, 59))
                .isEqualTo(new IncrementalSyncResult.Updated(59));

        assertThat(processedEvents.saved).containsKey(EVENT_ID);
    }

    @Test
    void anAlreadyProcessedNoticeDoesNotWaitForTheLockNorReadRedis(CapturedOutput output) throws InterruptedException {
        localVersion(57);
        processedEvents.saved.put(EVENT_ID, ProcessedEvent.builder().eventId(EVENT_ID).processedAt(NOW).build());
        Holder holder = Holder.start(lock);

        IncrementalSyncResult result = useCase().synchronizeCatalogIncrementally(EVENT_ID, 59);

        holder.releaseAndJoin();
        assertThat(result).isEqualTo(new IncrementalSyncResult.AlreadyProcessed());
        assertThat(metadataReads).isZero();
        assertThat(processedEvents.creates).isZero();
        assertThat(output).contains("Catalog update notice " + EVENT_ID + " was already processed: nothing to do");
    }

    @Test
    void aFailureOfTheCatchUpLeavesTheNoticeUnregistered() throws InterruptedException {
        localVersion(57);
        metadata = new CatalogMetadataResult.Unavailable("Redis did not answer in time");

        IncrementalSyncResult result = useCase().synchronizeCatalogIncrementally(EVENT_ID, 59);

        assertThat(((IncrementalSyncResult.Failed) result).reason()).startsWith("SOURCE_UNAVAILABLE");
        assertThat(recordedFailures).containsExactly(SyncErrorType.SOURCE_UNAVAILABLE);
        assertThat(processedEvents.saved).isEmpty();
        assertThat(lockIsFreeForAnotherThread()).isTrue();
    }

    @Test
    void aFailedFullSynchronizationLeavesTheNoticeUnregistered() {
        IncrementalSyncResult result = useCase().synchronizeCatalogIncrementally(EVENT_ID, 59);

        assertThat(result).isInstanceOf(IncrementalSyncResult.Failed.class);
        assertThat(processedEvents.saved).isEmpty();
    }

    @Test
    void aRegistrationRejectedByTheDatabaseIsLoggedAndKeepsTheResult(CapturedOutput output) {
        localVersion(57);
        processedEvents.createFailure = new IllegalStateException("database down");

        assertThat(useCase().synchronizeCatalogIncrementally(EVENT_ID, 59))
                .isEqualTo(new IncrementalSyncResult.Updated(59));

        assertThat(processedEvents.creates).isEqualTo(1);
        assertThat(recordedFailures).isEmpty();
        assertThat(output).contains("Catalog update notice " + EVENT_ID
                + " could not be registered as processed: IllegalStateException");
    }

    @Test
    void aFailureReadingTheProcessedNoticesWritesNothing(CapturedOutput output) throws InterruptedException {
        localVersion(57);
        processedEvents.findFailure = new IllegalStateException("database down");

        IncrementalSyncResult result = useCase().synchronizeCatalogIncrementally(EVENT_ID, 59);

        assertThat(result).isInstanceOf(IncrementalSyncResult.Failed.class);
        assertThat(metadataReads).isZero();
        assertThat(recordedFailures).isEmpty();
        assertThat(processedEvents.creates).isZero();
        assertThat(lockIsFreeForAnotherThread()).isTrue();
        assertThat(output).contains("Catalog update notice " + EVENT_ID
                + ": the processed notices could not be read: IllegalStateException");
    }

    @Test
    void aFailureReadingTheLocalVersionWritesNothingAndReleasesTheLock(CapturedOutput output)
            throws InterruptedException {
        stateFailure = new IllegalStateException("database down");

        IncrementalSyncResult result = useCase().synchronizeCatalogIncrementally(EVENT_ID, 59);

        assertThat(result).isInstanceOf(IncrementalSyncResult.Failed.class);
        assertThat(metadataReads).isZero();
        assertThat(recordedFailures).isEmpty();
        assertThat(processedEvents.creates).isZero();
        assertThat(lockIsFreeForAnotherThread()).isTrue();
        assertThat(output).contains("Catalog update notice " + EVENT_ID
                + ": the synchronization stopped: IllegalStateException");
    }

    @Test
    void aNoticeWaitsForAnotherSynchronizationAndReadsAgainAfterwards() throws InterruptedException {
        localVersion(57);
        Holder holder = Holder.start(lock);
        AtomicReference<IncrementalSyncResult> result = new AtomicReference<>();
        Thread notice = Thread.ofVirtual().start(() ->
                result.set(useCase().synchronizeCatalogIncrementally(EVENT_ID, 60)));
        Thread.sleep(200);
        assertThat(notice.isAlive()).isTrue();
        assertThat(metadataReads).isZero();

        localVersion(59);
        metadata = metadata(60, 50);
        holder.releaseAndJoin();
        notice.join(5000);

        assertThat(result.get()).isEqualTo(new IncrementalSyncResult.Updated(60));
        assertThat(requestedVersions).containsExactly(60L);
        assertThat(processedEvents.saved).containsKey(EVENT_ID);
    }

    @Test
    void anInterruptionWhileWaitingEndsWithoutProcessing() throws InterruptedException {
        localVersion(57);
        Holder holder = Holder.start(lock);
        AtomicReference<IncrementalSyncResult> result = new AtomicReference<>();
        Thread notice = Thread.ofVirtual().start(() ->
                result.set(useCase().synchronizeCatalogIncrementally(EVENT_ID, 59)));
        Thread.sleep(200);

        notice.interrupt();
        notice.join(5000);
        holder.releaseAndJoin();

        assertThat(result.get()).isInstanceOf(IncrementalSyncResult.Failed.class);
        assertThat(metadataReads).isZero();
        assertThat(recordedFailures).isEmpty();
        assertThat(processedEvents.creates).isZero();
    }

    private SynchronizeCatalogIncrementallyUseCaseImpl useCase() {
        CatalogCatchUp catchUp = new CatalogCatchUp(
                () -> {
                    if (stateFailure != null) {
                        throw stateFailure;
                    }
                    return state;
                },
                () -> {
                    if (fullResult instanceof FullSyncResult.Applied applied) {
                        state.recordSuccess(SyncType.FULL, applied.snapshotVersion(), NOW);
                    }
                    return fullResult;
                },
                () -> {
                    metadataReads++;
                    return metadata;
                },
                version -> {
                    requestedVersions.add(version);
                    return new CatalogVersionChangesResult.Obtained(
                            new CatalogVersionChanges(version, List.of(), List.of(), List.of()));
                },
                (version, categories, professionals, schedules) -> {
                    state.recordSuccess(SyncType.INCREMENTAL, version, NOW);
                    return new ApplyCatalogVersionResult.Applied(version);
                },
                recordedFailures::add,
                () -> {
                },
                new VerificationPolicy(3, Duration.ofMillis(1), 2, Duration.ofMillis(5)));
        return new SynchronizeCatalogIncrementallyUseCaseImpl(processedEvents, lock, catchUp,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void localVersion(long version) {
        state.recordSuccess(SyncType.FULL, version, NOW);
    }

    // Desde otro hilo: el candado es reentrante, así que el hilo que corrió el caso de uso lo tomaría igual.
    private boolean lockIsFreeForAnotherThread() throws InterruptedException {
        AtomicReference<Boolean> free = new AtomicReference<>(false);
        Thread other = Thread.ofVirtual().start(() -> {
            free.set(lock.tryAcquire());
            if (free.get()) {
                lock.release();
            }
        });
        other.join(5000);
        return free.get();
    }

    private static CatalogMetadataResult metadata(long current, long oldest) {
        return new CatalogMetadataResult.Obtained(new CatalogMetadata(current, oldest));
    }

    // Avisos procesados en memoria, que pueden fallar a pedido.
    private static final class FakeProcessedEvents implements ProcessedEventRepository {

        private final Map<String, ProcessedEvent> saved = new HashMap<>();
        private RuntimeException findFailure;
        private RuntimeException createFailure;
        private int creates;

        @Override
        public ProcessedEvent create(ProcessedEvent processedEvent) {
            creates++;
            if (createFailure != null) {
                throw createFailure;
            }
            saved.put(processedEvent.getEventId(), processedEvent);
            return processedEvent;
        }

        @Override
        public Optional<ProcessedEvent> findById(String eventId) {
            if (findFailure != null) {
                throw findFailure;
            }
            return Optional.ofNullable(saved.get(eventId));
        }
    }

    // Un hilo que toma el candado y lo suelta cuando el test lo pide, como otra sincronización en curso.
    private record Holder(Thread thread, CountDownLatch release) {

        static Holder start(SynchronizationLock lock) throws InterruptedException {
            CountDownLatch taken = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Thread thread = Thread.ofVirtual().start(() -> {
                lock.tryAcquire();
                taken.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.release();
                }
            });
            assertThat(taken.await(5, TimeUnit.SECONDS)).isTrue();
            return new Holder(thread, release);
        }

        void releaseAndJoin() throws InterruptedException {
            release.countDown();
            thread.join(5000);
        }
    }
}
