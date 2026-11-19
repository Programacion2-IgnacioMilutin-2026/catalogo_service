package catalogo.catalog.domain.ports.out;

import catalogo.catalog.domain.model.CatalogSyncState;

// Puerto de salida para utilizar repositorio del estado de sincronizacion.
public interface CatalogSyncStateRepository {

    CatalogSyncState get();

    void save(CatalogSyncState state);
}
