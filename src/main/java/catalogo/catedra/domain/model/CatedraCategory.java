package catalogo.catedra.domain.model;

// Categoría tal como la publica la cátedra, en el snapshot y en Redis.
public record CatedraCategory(
        long id,
        String name,
        String description,
        boolean enabled) {
}
