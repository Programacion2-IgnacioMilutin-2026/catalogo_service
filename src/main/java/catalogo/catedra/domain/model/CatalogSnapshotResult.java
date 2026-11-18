package catalogo.catedra.domain.model;

// Resultado del pedido del snapshot ya resuelto.
public sealed interface CatalogSnapshotResult {

    record Obtained(CatalogSnapshot snapshot) implements CatalogSnapshotResult {
    }

    // Sin sesión técnica.
    record Unavailable(String reason) implements CatalogSnapshotResult {
    }

    // La cátedra rechazó el pedido.
    record Rejected(String reason) implements CatalogSnapshotResult {
    }

    // La respuesta no se puede leer, le faltan datos o no es consistente.
    record Invalid(String reason) implements CatalogSnapshotResult {
    }
}
