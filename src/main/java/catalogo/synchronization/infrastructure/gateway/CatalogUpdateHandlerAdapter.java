package catalogo.synchronization.infrastructure.gateway;

import catalogo.catedra.domain.model.CatalogUpdateNotice;
import catalogo.catedra.domain.ports.out.CatalogUpdateHandler;
import catalogo.synchronization.application.service.SynchronizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Recibe los avisos de catedra y los procesa con la sincronización incremental. Implementa un puerto de catedra para que catedra entregue el aviso sin conocer a synchronization.
@Component
@RequiredArgsConstructor
public class CatalogUpdateHandlerAdapter implements CatalogUpdateHandler {

    private final SynchronizationService synchronizationService;

    @Override
    public void handleCatalogUpdate(CatalogUpdateNotice notice) {
        synchronizationService.synchronizeCatalogIncrementally(notice.eventId(), notice.newVersion());
    }
}
