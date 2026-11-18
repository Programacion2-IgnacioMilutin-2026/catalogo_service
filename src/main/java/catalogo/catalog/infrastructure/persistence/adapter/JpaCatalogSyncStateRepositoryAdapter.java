package catalogo.catalog.infrastructure.persistence.adapter;

import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.ports.out.CatalogSyncStateRepository;
import catalogo.catalog.infrastructure.persistence.entity.CatalogSyncStateEntity;
import catalogo.catalog.infrastructure.persistence.mapper.CatalogSyncStateMapper;
import catalogo.catalog.infrastructure.persistence.repository.JpaCatalogSyncStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Lee y guarda el estado de sincronización con JPA.
@Component
@RequiredArgsConstructor
public class JpaCatalogSyncStateRepositoryAdapter implements CatalogSyncStateRepository {

    private final JpaCatalogSyncStateRepository jpaCatalogSyncStateRepository;
    private final CatalogSyncStateMapper catalogSyncStateMapper;

    @Override
    public CatalogSyncState get() {
        return jpaCatalogSyncStateRepository.findById(CatalogSyncStateEntity.SINGLE_ROW_ID)
                .map(catalogSyncStateMapper::toDomainModel)
                .orElseThrow(() -> new IllegalStateException("The catalog sync state row is missing"));
    }

    @Override
    public void save(CatalogSyncState state) {
        jpaCatalogSyncStateRepository.save(catalogSyncStateMapper.toEntity(state));
    }
}
