package catalogo.catedra.domain.ports.in;

import catalogo.catedra.domain.model.CatalogUpdateNotice;

// Recibir un aviso válido de versión nueva del catálogo.
public interface ReceiveCatalogUpdateUseCase {

    void receiveCatalogUpdate(CatalogUpdateNotice notice);
}
