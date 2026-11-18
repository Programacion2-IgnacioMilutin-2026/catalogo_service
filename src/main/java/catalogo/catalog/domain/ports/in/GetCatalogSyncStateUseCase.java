package catalogo.catalog.domain.ports.in;

import catalogo.catalog.domain.model.CatalogSyncState;

// Puerto de entrada que catalog ofrece para leer el estado de sincronizacion.
public interface GetCatalogSyncStateUseCase {
    CatalogSyncState getCatalogSyncState();
}
