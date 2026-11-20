package catalogo.synchronization.application.usecases;

import catalogo.catedra.domain.ports.in.AwaitInitialTechnicalSessionUseCase;
import catalogo.synchronization.application.support.CatalogCatchUp;
import catalogo.synchronization.application.support.SynchronizationLock;
import catalogo.synchronization.domain.model.CatchUpResult;
import catalogo.synchronization.domain.model.VerificationResult;
import catalogo.synchronization.domain.ports.in.VerifyCatalogVersionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Verificación periódica. Compara la versión local con la de Redis y la pone al día.
@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyCatalogVersionUseCaseImpl implements VerifyCatalogVersionUseCase {

    private static final String LOG_NAME = "Catalog verification";

    private final SynchronizationLock synchronizationLock;
    private final AwaitInitialTechnicalSessionUseCase awaitInitialTechnicalSessionUseCase;
    private final CatalogCatchUp catalogCatchUp;

    @Override
    public VerificationResult verifyCatalogVersion() {
        awaitInitialTechnicalSessionUseCase.awaitInitialTechnicalSession();
        if (!synchronizationLock.tryAcquire()) {
            log.info("Catalog verification skipped: another synchronization is in progress");
            return new VerificationResult.Skipped();
        }
        try {
            return switch (catalogCatchUp.catchUp(LOG_NAME)) {
                case CatchUpResult.UpToDate upToDate -> new VerificationResult.UpToDate(upToDate.localVersion());
                case CatchUpResult.Updated updated -> new VerificationResult.Updated(updated.localVersion());
                case CatchUpResult.Failed failed -> new VerificationResult.Failed(failed.reason());
            };
        } finally {
            synchronizationLock.release();
        }
    }
}
