package catalogo.synchronization.domain.model;

// Resultado de una sincronización completa.
public sealed interface FullSyncResult {

    record Applied(long snapshotVersion) implements FullSyncResult {
    }

    record Failed(String reason) implements FullSyncResult {
    }

    record Discarded() implements FullSyncResult {
    }
}
