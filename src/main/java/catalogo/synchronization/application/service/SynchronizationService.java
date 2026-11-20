package catalogo.synchronization.application.service;

import catalogo.synchronization.domain.model.FullSyncResult;
import catalogo.synchronization.domain.model.IncrementalSyncResult;
import catalogo.synchronization.domain.model.VerificationResult;
import catalogo.synchronization.domain.ports.in.SynchronizeCatalogIncrementallyUseCase;
import catalogo.synchronization.domain.ports.in.SynchronizeFullCatalogUseCase;
import catalogo.synchronization.domain.ports.in.VerifyCatalogVersionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Servicio de las sincronizaciones del catálogo.
@Service
@RequiredArgsConstructor
public class SynchronizationService {

    private final SynchronizeFullCatalogUseCase synchronizeFullCatalogUseCase;
    private final VerifyCatalogVersionUseCase verifyCatalogVersionUseCase;
    private final SynchronizeCatalogIncrementallyUseCase synchronizeCatalogIncrementallyUseCase;

    public FullSyncResult synchronizeFullCatalog() {
        return synchronizeFullCatalogUseCase.synchronizeFullCatalog();
    }

    public VerificationResult verifyCatalogVersion() {
        return verifyCatalogVersionUseCase.verifyCatalogVersion();
    }

    public IncrementalSyncResult synchronizeCatalogIncrementally(String eventId, long newVersion) {
        return synchronizeCatalogIncrementallyUseCase.synchronizeCatalogIncrementally(eventId, newVersion);
    }
}
