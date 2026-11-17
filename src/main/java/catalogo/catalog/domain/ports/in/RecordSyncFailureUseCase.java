package catalogo.catalog.domain.ports.in;

import catalogo.catalog.domain.model.SyncErrorType;
import catalogo.catalog.domain.model.SyncType;

// Puerto de entrada que catalog ofrece para guardar el fallo de una sincronizacion.
public interface RecordSyncFailureUseCase {
    void recordSyncFailure(SyncType syncType, SyncErrorType errorType);
}
