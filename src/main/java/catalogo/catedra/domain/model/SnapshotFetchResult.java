package catalogo.catedra.domain.model;

// Resultado de un solo pedido del snapshot a la cátedra.
public sealed interface SnapshotFetchResult {

    record Received(CatalogSnapshot snapshot) implements SnapshotFetchResult {
    }

    // La cátedra rechazó el JWT técnico.
    record TokenRejected() implements SnapshotFetchResult {
    }

    // Otro rechazo de la cátedra.
    record Rejected(String reason) implements SnapshotFetchResult {
    }

    // Sin respuesta, timeout, corte o error del servidor.
    record Unavailable(String reason) implements SnapshotFetchResult {
    }

    // La respuesta llegó, pero no se puede leer o le faltan datos.
    record Invalid(String reason) implements SnapshotFetchResult {
    }
}
