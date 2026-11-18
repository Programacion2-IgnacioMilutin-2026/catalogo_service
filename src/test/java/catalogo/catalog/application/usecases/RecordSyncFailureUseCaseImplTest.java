package catalogo.catalog.application.usecases;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogSamples;
import catalogo.catalog.CatalogTables;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.SyncErrorType;
import catalogo.catalog.domain.model.SyncType;
import catalogo.catalog.domain.ports.in.RecordSyncFailureUseCase;
import catalogo.catalog.domain.ports.in.ReplaceCatalogUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// El registro de una sincronización fallida contra un PostgreSQL real.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RecordSyncFailureUseCaseImplTest {

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
    void aFailureAfterASuccessKeepsTheCopyTheVersionAndTheLastSuccess() {
        ProfessionalCategory category = CatalogSamples.category(1);
        Professional professional = CatalogSamples.professional(15, category);
        replaceCatalogUseCase.replaceCatalog(303, List.of(category), List.of(professional),
                List.of(CatalogSamples.weeklySchedule(21, professional)));
        Instant lastSuccess = instant(tables.syncState(), "last_successful_sync_at");

        recordSyncFailureUseCase.recordSyncFailure(SyncType.FULL, SyncErrorType.SOURCE_UNAVAILABLE);

        Map<String, Object> state = tables.syncState();
        assertThat(state)
                .containsEntry("local_version", 303L)
                .containsEntry("last_sync_type", "FULL")
                .containsEntry("last_sync_result", "ERROR")
                .containsEntry("last_error_type", "SOURCE_UNAVAILABLE");
        assertThat(instant(state, "last_successful_sync_at")).isEqualTo(lastSuccess);
        assertThat(instant(state, "last_sync_at")).isAfterOrEqualTo(lastSuccess);
        assertThat(tables.ids("weekly_schedules")).containsExactly(21L);
    }

    @Test
    void aFailureBeforeAnySuccessIsRecordedAndTheCatalogStaysNotInitialized() {
        recordSyncFailureUseCase.recordSyncFailure(SyncType.FULL, SyncErrorType.INTEGRATION_REJECTED);

        assertThat(tables.syncState())
                .containsEntry("local_version", null)
                .containsEntry("last_successful_sync_at", null)
                .containsEntry("last_sync_result", "ERROR")
                .containsEntry("last_error_type", "INTEGRATION_REJECTED");
    }

    private static Instant instant(Map<String, Object> row, String column) {
        return ((Timestamp) row.get(column)).toInstant();
    }
}
