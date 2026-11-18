package catalogo.synchronization;

import catalogo.FakeHttpServer.Response;
import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catedra.FakeCatedraServer;
import catalogo.catedra.application.service.TechnicalSessionService;
import catalogo.synchronization.application.service.SynchronizationService;
import catalogo.synchronization.domain.model.FullSyncResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// La sincronización completa con la aplicación entera, una cátedra simulada y un PostgreSQL real.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FullSynchronizationIntegrationTest {

    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();

    @Autowired
    private SynchronizationService synchronizationService;

    @Autowired
    private TechnicalSessionService technicalSessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CatalogTables tables;

    @DynamicPropertySource
    static void catedraProperties(DynamicPropertyRegistry registry) {
        registry.add("catalogo.catedra.base-url", CATEDRA::baseUrl);
        registry.add("catalogo.catedra.username", () -> FakeCatedraServer.TEST_USERNAME);
        registry.add("catalogo.catedra.password", () -> FakeCatedraServer.TEST_PASSWORD);
        registry.add("catalogo.catedra.snapshot.initial-delay", () -> "10ms");
        registry.add("catalogo.catedra.snapshot.max-delay", () -> "20ms");
    }

    @AfterAll
    static void stopCatedra() {
        CATEDRA.close();
    }

    @BeforeEach
    void emptyCatalogAndEnabledSession() {
        CATEDRA.respondToSnapshot(FakeCatedraServer.validSnapshot());
        tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        await().atMost(Duration.ofSeconds(10))
                .until(() -> technicalSessionService.getTechnicalSession().isPresent());
    }

    @Test
    void anEmptyCatalogIsInitializedFromTheSnapshotWithoutShowingTheToken(CapturedOutput output) {
        int requestsBefore = CATEDRA.snapshotRequests().size();
        long start = System.nanoTime();

        FullSyncResult result = synchronizationService.synchronizeFullCatalog();

        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        System.out.println("Full synchronization took " + elapsed.toMillis() + " ms");
        assertThat(result).isEqualTo(new FullSyncResult.Applied(57));
        assertThat(tables.ids("professional_categories")).containsExactly(501L, 502L);
        assertThat(tables.ids("professionals")).containsExactly(601L, 602L, 603L);
        assertThat(tables.ids("weekly_schedules")).containsExactly(701L, 702L, 703L, 704L);
        assertThat(tables.categories().getFirst()).containsEntry("name", "Kinesiología");
        assertThat(tables.professionals().getFirst())
                .containsEntry("first_name", "Valentina")
                .containsEntry("enabled", true);
        assertThat(tables.professionals().get(1))
                .containsEntry("category_id", 501L)
                .containsEntry("last_name", "Ferreyra")
                .containsEntry("enabled", false);
        assertThat(tables.weeklySchedules().get(2))
                .containsEntry("professional_id", 602L)
                .containsEntry("day_of_week", "TUESDAY")
                .containsEntry("start_time", Time.valueOf("09:00:00"))
                .containsEntry("slot_duration_minutes", 20)
                .containsEntry("enabled", false);
        Map<String, Object> state = tables.syncState();
        assertThat(state)
                .containsEntry("local_version", 57L)
                .containsEntry("last_sync_type", "FULL")
                .containsEntry("last_sync_result", "SUCCESS")
                .containsEntry("last_error_type", null);
        assertThat(state.get("last_successful_sync_at")).isEqualTo(state.get("last_sync_at")).isNotNull();
        assertThat(CATEDRA.snapshotRequests()).hasSize(requestsBefore + 1);
        assertThat(CATEDRA.snapshotRequests().getLast().header("Authorization"))
                .isEqualTo("Bearer " + FakeCatedraServer.TEST_TOKEN);
        assertThat(output)
                .contains("Catalog snapshot 57 received: 2 categories, 3 professionals, 4 weekly schedules")
                .contains("Full catalog synchronization applied version 57")
                .doesNotContain(FakeCatedraServer.TEST_TOKEN)
                .doesNotContain(FakeCatedraServer.TEST_PASSWORD);
    }

    @Test
    void aFailureAfterASuccessKeepsTheCopyTheVersionAndTheLastSuccess(CapturedOutput output) {
        assertThat(synchronizationService.synchronizeFullCatalog()).isEqualTo(new FullSyncResult.Applied(57));
        Timestamp lastSuccess = (Timestamp) tables.syncState().get("last_successful_sync_at");
        CATEDRA.respondToSnapshot(Response.empty(503));
        int requestsBefore = CATEDRA.snapshotRequests().size();

        FullSyncResult result = synchronizationService.synchronizeFullCatalog();

        assertThat(result).isInstanceOf(FullSyncResult.Failed.class);
        assertThat(((FullSyncResult.Failed) result).reason()).startsWith("SOURCE_UNAVAILABLE");
        assertThat(CATEDRA.snapshotRequests()).hasSize(requestsBefore + 5);
        Map<String, Object> state = tables.syncState();
        assertThat(state)
                .containsEntry("local_version", 57L)
                .containsEntry("last_successful_sync_at", lastSuccess)
                .containsEntry("last_sync_result", "ERROR")
                .containsEntry("last_error_type", "SOURCE_UNAVAILABLE");
        assertThat(((Timestamp) state.get("last_sync_at")).toInstant()).isAfter(lastSuccess.toInstant());
        assertThat(tables.ids("weekly_schedules")).containsExactly(701L, 702L, 703L, 704L);
        assertThat(output)
                .contains("Catalog snapshot attempt 5/5 failed: Central API answered HTTP 503")
                .contains("Full catalog synchronization failed with SOURCE_UNAVAILABLE")
                .doesNotContain(FakeCatedraServer.TEST_TOKEN);
    }

    @Test
    void aSnapshotWithoutCategoriesDoesNotEmptyTheCopy(CapturedOutput output) {
        assertThat(synchronizationService.synchronizeFullCatalog()).isEqualTo(new FullSyncResult.Applied(57));
        ObjectNode empty = FakeCatedraServer.snapshotTree();
        empty.put("snapshotVersion", 58);
        empty.putArray("professionalCategories");
        empty.putArray("professionals");
        empty.putArray("weeklySchedules");
        CATEDRA.respondToSnapshot(FakeCatedraServer.snapshotOf(empty));

        FullSyncResult result = synchronizationService.synchronizeFullCatalog();

        assertThat(result).isInstanceOf(FullSyncResult.Failed.class);
        assertThat(((FullSyncResult.Failed) result).reason()).startsWith("INVALID_DATA");
        assertThat(tables.ids("professional_categories")).containsExactly(501L, 502L);
        assertThat(tables.ids("professionals")).containsExactly(601L, 602L, 603L);
        assertThat(tables.syncState())
                .containsEntry("local_version", 57L)
                .containsEntry("last_error_type", "INVALID_DATA");
        assertThat(output).contains("Catalog snapshot 58 is not consistent: [no professional categories]");
    }
}
