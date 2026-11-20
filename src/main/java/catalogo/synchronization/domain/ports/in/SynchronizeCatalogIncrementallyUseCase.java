package catalogo.synchronization.domain.ports.in;

import catalogo.synchronization.domain.model.IncrementalSyncResult;

// Procesar un aviso de versión nueva del catálogo para hacer una sincronizacion incremental.
public interface SynchronizeCatalogIncrementallyUseCase {

    IncrementalSyncResult synchronizeCatalogIncrementally(String eventId, long newVersion);
}
