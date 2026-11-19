package catalogo.catalog.application.usecases;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogSamples;
import catalogo.catalog.CatalogTables;
import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.SyncErrorType;
import catalogo.catalog.domain.model.SyncResult;
import catalogo.catalog.domain.model.SyncType;
import catalogo.catalog.domain.ports.in.GetCatalogSyncStateUseCase;
import catalogo.catalog.domain.ports.in.RecordSyncFailureUseCase;
import catalogo.catalog.domain.ports.in.ReplaceCatalogUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// La lectura del estado de sincronización contra un PostgreSQL real.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GetCatalogSyncStateUseCaseImplTest {

    @Autowired
    private GetCatalogSyncStateUseCase getCatalogSyncStateUseCase;

    @Autowired
    private RecordSyncFailureUseCase recordSyncFailureUseCase;

    @Autowired
    private ReplaceCatalogUseCase replaceCatalogUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CatalogTables tables;

    @BeforeEach
    void emptyCatalog() {
        tables = new CatalogTables(jdbcTemplate);
        tables.reset();
    }

    @Test
    void anEmptyCatalogIsNotInitialized() {
        CatalogSyncState state = getCatalogSyncStateUseCase.getCatalogSyncState();

        assertThat(state.isInitialized()).isFalse();
        assertThat(state.getLastSyncResult()).isNull();
    }

    @Test
    void theStateReadIsTheOneStored() {
        ProfessionalCategory category = CatalogSamples.category(1);
        Professional professional = CatalogSamples.professional(15, category);
        replaceCatalogUseCase.replaceCatalog(303, List.of(category), List.of(professional), List.of());
        recordSyncFailureUseCase.recordSyncFailure(SyncErrorType.INVALID_DATA);

        CatalogSyncState state = getCatalogSyncStateUseCase.getCatalogSyncState();

        assertThat(state.getLocalVersion()).isEqualTo(303L);
        assertThat(state.getLastSyncType()).isEqualTo(SyncType.FULL);
        assertThat(state.getLastSyncResult()).isEqualTo(SyncResult.ERROR);
        assertThat(state.getLastErrorType()).isEqualTo(SyncErrorType.INVALID_DATA);
        assertThat(state.getLastSuccessfulSyncAt())
                .isEqualTo(((Timestamp) tables.syncState().get("last_successful_sync_at")).toInstant());
        assertThat(state.getLastSyncAt())
                .isEqualTo(((Timestamp) tables.syncState().get("last_sync_at")).toInstant());
    }
}
