package catalogo.catedra.domain.ports.out;

import catalogo.catedra.domain.model.CatalogUpdateNotice;

// Quien procesa los avisos de versión nueva del catálogo.
public interface CatalogUpdateHandler {

    void handleCatalogUpdate(CatalogUpdateNotice notice);
}
