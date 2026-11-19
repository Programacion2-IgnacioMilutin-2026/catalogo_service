package catalogo.catalog.infrastructure.persistence.entity;

import catalogo.catalog.domain.model.SyncErrorType;
import catalogo.catalog.domain.model.SyncResult;
import catalogo.catalog.domain.model.SyncType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// La única fila del estado de sincronización.
@Getter
@Setter
@Entity
@Table(name = "catalog_sync_state")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogSyncStateEntity {
    public static final Integer SINGLE_ROW_ID = 1;

    @Id
    private Integer id;
    private Long localVersion;
    private Instant lastSyncAt;
    private Instant lastSuccessfulSyncAt;
    @Enumerated(EnumType.STRING)
    private SyncType lastSyncType;
    @Enumerated(EnumType.STRING)
    private SyncResult lastSyncResult;
    @Enumerated(EnumType.STRING)
    private SyncErrorType lastErrorType;
}
