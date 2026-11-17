package catalogo.synchronization.application.service;

import catalogo.synchronization.domain.model.FullSyncResult;
import catalogo.synchronization.domain.ports.in.SynchronizeFullCatalogUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Servicio de las sincronizaciones del catálogo.
@Service
@RequiredArgsConstructor
public class SynchronizationService {

    private final SynchronizeFullCatalogUseCase synchronizeFullCatalogUseCase;

    public FullSyncResult synchronizeFullCatalog() {
        return synchronizeFullCatalogUseCase.synchronizeFullCatalog();
    }
}
