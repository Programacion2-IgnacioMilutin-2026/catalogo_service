package catalogo.catalog.application.usecases;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogSamples;
import catalogo.catalog.CatalogTables;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.SyncErrorType;
import catalogo.catalog.domain.ports.in.RecordSyncFailureUseCase;
import catalogo.catalog.domain.ports.in.RecordUpToDateCheckUseCase;
import catalogo.catalog.domain.ports.in.ReplaceCatalogUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// El registro de una verificación con el catálogo al día contra un PostgreSQL real.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RecordUpToDateCheckUseCaseImplTest {

    @Autowired
    private RecordUpToDateCheckUseCase recordUpToDateCheckUseCase;

    @Autowired
    private RecordSyncFailureUseCase recordSyncFailureUseCase;

    @Autowired
    private ReplaceCatalogUseCase replaceCatalogUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CatalogTables tables;

    @BeforeEach
    void catalogWithASuccess() {
        tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        ProfessionalCategory category = CatalogSamples.category(1);
        Professional professional = CatalogSamples.professional(15, category);
        replaceCatalogUseCase.replaceCatalog(303, List.of(category), List.of(professional),
                List.of(CatalogSamples.weeklySchedule(21, professional)));
    }

    @Test
    void aCheckAfterASuccessDoesNotWriteAnything() {
        Map<String, Object> before = tables.syncState();

        recordUpToDateCheckUseCase.recordUpToDateCheck();

        assertThat(tables.syncState()).isEqualTo(before);
    }

    @Test
    void aCheckAfterAFailureRecordsASuccessfulAttemptWithoutTouchingTheVersionOrTheLastSuccess() {
        Timestamp lastSuccess = (Timestamp) tables.syncState().get("last_successful_sync_at");
        recordSyncFailureUseCase.recordSyncFailure(SyncErrorType.SOURCE_UNAVAILABLE);
        Timestamp failedAt = (Timestamp) tables.syncState().get("last_sync_at");

        recordUpToDateCheckUseCase.recordUpToDateCheck();

        Map<String, Object> state = tables.syncState();
        assertThat(state)
                .containsEntry("local_version", 303L)
                .containsEntry("last_successful_sync_at", lastSuccess)
                .containsEntry("last_sync_type", "FULL")
                .containsEntry("last_sync_result", "SUCCESS")
                .containsEntry("last_error_type", null);
        assertThat(((Timestamp) state.get("last_sync_at")).toInstant()).isAfterOrEqualTo(failedAt.toInstant());
    }
}
