package catalogo.catedra.domain.model;

// Resultado de pedir una versión del catálogo.
public sealed interface CatalogVersionChangesResult {

    record Obtained(CatalogVersionChanges changes) implements CatalogVersionChangesResult {
    }

    // Sin sesión técnica, o Redis no respondió después de los reintentos.
    record Unavailable(String reason) implements CatalogVersionChangesResult {
    }

    // Redis rechazó las credenciales también con la sesión renovada.
    record Rejected(String reason) implements CatalogVersionChangesResult {
    }

    // La versión publicada no se puede interpretar o le falta una entidad.
    record Invalid(String reason) implements CatalogVersionChangesResult {
    }
}
