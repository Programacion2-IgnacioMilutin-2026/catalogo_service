package catalogo.catedra.domain.model;

// Categoría tal como la entrega el snapshot de la cátedra.
public record SnapshotCategory(
        long id,
        String name,
        String description,
        boolean enabled) {
}
