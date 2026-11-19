package catalogo.catedra.domain.model;

// Profesional tal como lo publica la cátedra, en el snapshot y en Redis.
public record CatedraProfessional(
        long id,
        long categoryId,
        String firstName,
        String lastName,
        boolean enabled) {
}
