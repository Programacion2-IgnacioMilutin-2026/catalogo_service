package catalogo.catedra.domain.model;

// Metadata del catálogo que la cátedra publica en Redis.
public record CatalogMetadata(long currentVersion, long oldestAvailableVersion) {
}
