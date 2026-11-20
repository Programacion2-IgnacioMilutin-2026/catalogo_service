package catalogo.catedra.domain.ports.in;

import catalogo.catedra.domain.model.CatalogVersionChangesResult;

// Puerto de entrada para leer en Redis una versión del catálogo.
public interface GetCatalogVersionChangesUseCase {
    CatalogVersionChangesResult getCatalogVersionChanges(long version);
}
