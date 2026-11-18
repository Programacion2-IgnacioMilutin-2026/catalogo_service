package catalogo.catedra.domain.model;

// Profesional tal como lo entrega el snapshot de la cátedra.
public record SnapshotProfessional(
        long id,
        long categoryId,
        String firstName,
        String lastName,
        boolean enabled) {
}
