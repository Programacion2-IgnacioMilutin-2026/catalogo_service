package catalogo.synchronization.application.usecases;

import catalogo.catalog.domain.model.ApplyCatalogVersionResult;
import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.model.SyncType;
import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogMetadataResult;
import catalogo.catedra.domain.model.CatalogVersionChanges;
import catalogo.catedra.domain.model.CatalogVersionChangesResult;
import catalogo.synchronization.application.support.CatalogCatchUp;
import catalogo.synchronization.application.support.SynchronizationLock;
import catalogo.synchronization.domain.model.FullSyncResult;
import catalogo.synchronization.domain.model.VerificationPolicy;
import catalogo.synchronization.domain.model.VerificationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// La verificación de la versión del catálogo: espera el login, se omite si hay otra sincronización, y traduce lo que
// hizo la pieza común. Lo que hace la pieza se prueba en CatalogCatchUpTest.
@ExtendWith(OutputCaptureExtension.class)
class VerifyCatalogVersionUseCaseImplTest {

    private final SynchronizationLock lock = new SynchronizationLock();
    private final CatalogSyncState state = new CatalogSyncState();
    private CatalogMetadataResult metadata = new CatalogMetadataResult.Obtained(new CatalogMetadata(59, 50));
    private final FullSyncResult fullResult = new FullSyncResult.Failed("SOURCE_UNAVAILABLE: not configured in the test");
    private int metadataReads;
    private int initialLoginWaits;

    @Test
    void anotherSynchronizationInProgressSkipsTheVerification(CapturedOutput output) throws InterruptedException {
        localVersion(56);
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
        assertThat(output).contains("Catalog verification skipped: another synchronization is in progress");
    }

    @Test
    void theVerificationWaitsForTheInitialLoginBeforeTakingTheLock() {
        localVersion(59);

        useCase().verifyCatalogVersion();

        assertThat(initialLoginWaits).isEqualTo(1);
    }

    @Test
    void theVerificationUsesTheCatchUpWithItsNameAndTranslatesEachResult(CapturedOutput output) {
        localVersion(59);
        assertThat(useCase().verifyCatalogVersion()).isEqualTo(new VerificationResult.UpToDate(59));
        assertThat(output).contains("Catalog verification: up to date at version 59");

        localVersion(58);
        assertThat(useCase().verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(59));

        metadata = new CatalogMetadataResult.Unavailable("Redis did not answer in time");
        assertThat(((VerificationResult.Failed) useCase().verifyCatalogVersion()).reason())
                .startsWith("SOURCE_UNAVAILABLE");
        assertThat(output).contains("Catalog verification failed with SOURCE_UNAVAILABLE");

        assertThat(lock.tryAcquire()).isTrue();
    }

    private VerifyCatalogVersionUseCaseImpl useCase() {
        CatalogCatchUp catchUp = new CatalogCatchUp(() -> state,
                () -> fullResult,
                () -> {
                    metadataReads++;
                    return metadata;
                },
                version -> new CatalogVersionChangesResult.Obtained(
                        new CatalogVersionChanges(version, List.of(), List.of(), List.of())),
                (version, categories, professionals, schedules) -> {
                    state.recordSuccess(SyncType.INCREMENTAL, version, Instant.now());
                    return new ApplyCatalogVersionResult.Applied(version);
                },
                errorType -> {
                },
                () -> {
                },
                new VerificationPolicy(3, Duration.ofMillis(1), 2, Duration.ofMillis(5)));
        return new VerifyCatalogVersionUseCaseImpl(lock,
                () -> {
                    initialLoginWaits++;
                    return Optional.empty();
                },
                catchUp);
    }

    private void localVersion(long version) {
        state.recordSuccess(SyncType.FULL, version, Instant.parse("2026-11-18T12:00:00Z"));
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
