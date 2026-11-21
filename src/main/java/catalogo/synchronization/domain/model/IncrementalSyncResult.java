package catalogo.synchronization.domain.model;

// Resultado de procesar un aviso de versión nueva del catálogo en un sincronizacion .
public sealed interface IncrementalSyncResult {

    record AlreadyProcessed() implements IncrementalSyncResult {
    }

    record UpToDate(long localVersion) implements IncrementalSyncResult {
    }

    record Updated(long localVersion) implements IncrementalSyncResult {
    }

    record Failed(String reason) implements IncrementalSyncResult {
    }
}
