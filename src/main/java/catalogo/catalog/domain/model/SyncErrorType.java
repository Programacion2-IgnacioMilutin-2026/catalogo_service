package catalogo.catalog.domain.model;

// Fallos de una sincronización.
public enum SyncErrorType {
    SOURCE_UNAVAILABLE,
    INTEGRATION_REJECTED,
    INVALID_DATA,
    PERSISTENCE_FAILURE
}
