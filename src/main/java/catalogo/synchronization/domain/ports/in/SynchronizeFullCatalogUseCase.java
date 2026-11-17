package catalogo.synchronization.domain.ports.in;

import catalogo.synchronization.domain.model.FullSyncResult;

// Puerto de entrada para realizar una sincronizacion completa.
public interface SynchronizeFullCatalogUseCase {
    FullSyncResult synchronizeFullCatalog();
}
