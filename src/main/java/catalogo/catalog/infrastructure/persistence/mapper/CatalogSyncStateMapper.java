package catalogo.catalog.infrastructure.persistence.mapper;

import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.infrastructure.persistence.entity.CatalogSyncStateEntity;
import org.springframework.stereotype.Component;

// Convierte el estado de sincronización entre el dominio y la entidad.
@Component
public class CatalogSyncStateMapper {

    public CatalogSyncStateEntity toEntity(CatalogSyncState state) {
        if (state == null) {
            return null;
        }
        return CatalogSyncStateEntity.builder()
                .id(CatalogSyncStateEntity.SINGLE_ROW_ID)
                .localVersion(state.getLocalVersion())
                .lastSyncAt(state.getLastSyncAt())
                .lastSuccessfulSyncAt(state.getLastSuccessfulSyncAt())
                .lastSyncType(state.getLastSyncType())
                .lastSyncResult(state.getLastSyncResult())
                .lastErrorType(state.getLastErrorType())
                .build();
    }

    public CatalogSyncState toDomainModel(CatalogSyncStateEntity entity) {
        if (entity == null) {
            return null;
        }
        return CatalogSyncState.builder()
                .localVersion(entity.getLocalVersion())
                .lastSyncAt(entity.getLastSyncAt())
                .lastSuccessfulSyncAt(entity.getLastSuccessfulSyncAt())
                .lastSyncType(entity.getLastSyncType())
                .lastSyncResult(entity.getLastSyncResult())
                .lastErrorType(entity.getLastErrorType())
                .build();
    }
}
