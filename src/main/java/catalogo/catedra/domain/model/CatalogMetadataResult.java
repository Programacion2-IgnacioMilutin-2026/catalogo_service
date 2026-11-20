package catalogo.catedra.domain.model;

// Resultado de pedir la metadata del catálogo.
public sealed interface CatalogMetadataResult {

    record Obtained(CatalogMetadata metadata) implements CatalogMetadataResult {
    }

    // Sin sesión técnica, o Redis no respondió después de los reintentos.
    record Unavailable(String reason) implements CatalogMetadataResult {
    }

    // Redis rechazó las credenciales también con la sesión renovada.
    record Rejected(String reason) implements CatalogMetadataResult {
    }

    // La metadata publicada no se puede interpretar.
    record Invalid(String reason) implements CatalogMetadataResult {
    }
}
