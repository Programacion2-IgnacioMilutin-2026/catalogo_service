package catalogo.catedra.application.usecases;

import catalogo.catedra.domain.model.CatalogUpdateNotice;
import catalogo.catedra.domain.ports.in.ReceiveCatalogUpdateUseCase;
import catalogo.catedra.domain.ports.out.CatalogUpdateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Entrega un aviso válido a quien procesa los avisos.
@Component
@RequiredArgsConstructor
public class ReceiveCatalogUpdateUseCaseImpl implements ReceiveCatalogUpdateUseCase {

    private final CatalogUpdateHandler catalogUpdateHandler;

    @Override
    public void receiveCatalogUpdate(CatalogUpdateNotice notice) {
        catalogUpdateHandler.handleCatalogUpdate(notice);
    }
}
