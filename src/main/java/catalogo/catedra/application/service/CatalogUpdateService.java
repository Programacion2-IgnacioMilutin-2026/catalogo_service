package catalogo.catedra.application.service;

import catalogo.catedra.domain.model.CatalogUpdateNotice;
import catalogo.catedra.domain.ports.in.ReceiveCatalogUpdateUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Servicio de los avisos de versión nueva del catálogo que llegan de la cátedra.
@Service
@RequiredArgsConstructor
public class CatalogUpdateService {

    private final ReceiveCatalogUpdateUseCase receiveCatalogUpdateUseCase;

    public void receiveCatalogUpdate(CatalogUpdateNotice notice) {
        receiveCatalogUpdateUseCase.receiveCatalogUpdate(notice);
    }
}
