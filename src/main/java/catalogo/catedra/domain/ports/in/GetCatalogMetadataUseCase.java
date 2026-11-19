package catalogo.catedra.domain.ports.in;

import catalogo.catedra.domain.model.CatalogMetadataResult;

// Puerto de entrada para leer la metadata del catálogo en Redis.
public interface GetCatalogMetadataUseCase {
    CatalogMetadataResult getCatalogMetadata();
}
