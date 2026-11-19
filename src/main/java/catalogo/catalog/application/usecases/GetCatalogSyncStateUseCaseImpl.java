package catalogo.catalog.application.usecases;

import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.ports.in.GetCatalogSyncStateUseCase;
import catalogo.catalog.domain.ports.out.CatalogSyncStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Devuelve el estado de sincronización guardado.
@Component
@RequiredArgsConstructor
public class GetCatalogSyncStateUseCaseImpl implements GetCatalogSyncStateUseCase {

    private final CatalogSyncStateRepository catalogSyncStateRepository;

    @Override
    public CatalogSyncState getCatalogSyncState() {
        return catalogSyncStateRepository.get();
    }
}
