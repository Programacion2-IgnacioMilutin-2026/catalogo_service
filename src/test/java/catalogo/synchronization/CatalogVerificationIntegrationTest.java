package catalogo.synchronization;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catedra.CatedraRedis;
import catalogo.catedra.FakeCatedraServer;
import catalogo.catedra.application.service.TechnicalSessionService;
import catalogo.synchronization.application.service.SynchronizationService;
import catalogo.synchronization.domain.model.VerificationResult;
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
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// La verificación con la aplicación entera.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CatalogVerificationIntegrationTest {

    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();
    private static final CatedraRedis REDIS = CatedraRedis.start();

    static {
        CATEDRA.respondToLogin(FakeCatedraServer.loginWithRedis(REDIS.host(), REDIS.port(), CatedraRedis.USERNAME,
                CatedraRedis.PASSWORD));
    }

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
        registry.add("catalogo.catedra.redis.initial-delay", () -> "10ms");
        registry.add("catalogo.catedra.redis.max-delay", () -> "20ms");
    }

    @AfterAll
    static void stopCatedraAndRedis() {
        CATEDRA.close();
        REDIS.close();
    }

    @BeforeEach
    void emptyCatalogAndRedisAtVersion57() {
        CATEDRA.respondToSnapshot(FakeCatedraServer.validSnapshot());
        REDIS.clear();
        REDIS.putMetadata(57, 50);
        REDIS.putSnapshotEntities();
        tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        await().atMost(Duration.ofSeconds(10))
                .until(() -> technicalSessionService.getTechnicalSession().isPresent());
    }

    @Test
    void anEmptyCatalogIsLoadedFromTheSnapshotAndThenCheckedAgainstRedis(CapturedOutput output) {
        assertThat(synchronizationService.verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(57));

        assertThat(tables.syncState())
                .containsEntry("local_version", 57L)
                .containsEntry("last_sync_type", "FULL")
                .containsEntry("last_sync_result", "SUCCESS");
        assertThat(tables.ids("professionals")).containsExactly(601L, 602L, 603L);
        assertThat(output)
                .contains("Catalog verification: no local version, requesting the full synchronization")
                .contains("Catalog verification: up to date at version 57");
        assertNoSecrets(output);
    }

    @Test
    void aCatalogBehindAppliesTheVersionsPublishedInRedis(CapturedOutput output) {
        loadedAtVersion57();
        int snapshotsBefore = CATEDRA.snapshotRequests().size();
        ObjectNode renamed = CatedraRedis.snapshotEntity("professionals", 601);
        renamed.put("lastName", "Ibáñez Pérez");
        REDIS.putEntity(CatedraRedis.PROFESSIONALS, 601, renamed.toString());
        ObjectNode disabled = CatedraRedis.snapshotEntity("professionalCategories", 502);
        disabled.put("enabled", false);
        REDIS.putEntity(CatedraRedis.CATEGORIES, 502, disabled.toString());
        ObjectNode moved = CatedraRedis.snapshotEntity("weeklySchedules", 702);
        moved.put("startTime", "15:00:00");
        REDIS.putEntity(CatedraRedis.WEEKLY_SCHEDULES, 702, moved.toString());
        REDIS.putChanges(58, List.of(), List.of(601L), List.of());
        REDIS.putChanges(59, List.of(502L), List.of(), List.of(702L));
        REDIS.putMetadata(59, 50);

        assertThat(synchronizationService.verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(59));

        assertThat(row(tables.professionals(), 601)).containsEntry("last_name", "Ibáñez Pérez");
        assertThat(row(tables.categories(), 502)).containsEntry("enabled", false);
        assertThat(row(tables.weeklySchedules(), 702)).containsEntry("start_time", Time.valueOf("15:00:00"));
        assertThat(tables.ids("professional_categories")).containsExactly(501L, 502L);
        assertThat(tables.ids("professionals")).containsExactly(601L, 602L, 603L);
        assertThat(tables.ids("weekly_schedules")).containsExactly(701L, 702L, 703L, 704L);
        assertThat(tables.syncState())
                .containsEntry("local_version", 59L)
                .containsEntry("last_sync_type", "INCREMENTAL")
                .containsEntry("last_sync_result", "SUCCESS");
        assertThat(CATEDRA.snapshotRequests()).hasSize(snapshotsBefore);
        assertThat(output).contains("Catalog verification applied versions 58 to 59: local version 59");
        assertNoSecrets(output);
    }

    @Test
    void aLocalVersionOlderThanTheHistoryIsRebuiltFromTheSnapshot() {
        loadedAtVersion57();
        REDIS.putMetadata(61, 60);
        ObjectNode snapshot61 = FakeCatedraServer.snapshotTree();
        snapshot61.put("snapshotVersion", 61);
        CATEDRA.respondToSnapshot(FakeCatedraServer.snapshotOf(snapshot61));

        assertThat(synchronizationService.verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(61));

        assertThat(tables.syncState())
                .containsEntry("local_version", 61L)
                .containsEntry("last_sync_type", "FULL");
    }

    @Test
    void malformedChangesInRedisAreADiscontinuityResolvedWithTheSnapshot(CapturedOutput output) {
        loadedAtVersion57();
        REDIS.putRaw(CatedraRedis.changesKey(58), "not json");
        REDIS.putMetadata(58, 50);
        ObjectNode snapshot58 = FakeCatedraServer.snapshotTree();
        snapshot58.put("snapshotVersion", 58);
        CATEDRA.respondToSnapshot(FakeCatedraServer.snapshotOf(snapshot58));

        assertThat(synchronizationService.verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(58));

        assertThat(tables.syncState()).containsEntry("local_version", 58L).containsEntry("last_sync_type", "FULL");
        assertThat(output).contains("Catalog verification found a discontinuity, requesting the full synchronization");
    }

    @Test
    void aDiscontinuityThatPersistsAfterTheSnapshotIsRecordedAsInvalidData() {
        loadedAtVersion57();
        REDIS.admin().del(CatedraRedis.METADATA);
        int snapshotsBefore = CATEDRA.snapshotRequests().size();

        VerificationResult result = synchronizationService.verifyCatalogVersion();

        assertThat(((VerificationResult.Failed) result).reason()).startsWith("INVALID_DATA");
        assertThat(CATEDRA.snapshotRequests()).hasSize(snapshotsBefore + 1);
        Map<String, Object> state = tables.syncState();
        assertThat(state)
                .containsEntry("local_version", 57L)
                .containsEntry("last_sync_type", "FULL")
                .containsEntry("last_sync_result", "ERROR")
                .containsEntry("last_error_type", "INVALID_DATA");
    }

    @Test
    void anUpToDateVerificationAfterAFailureRecordsASuccessfulAttempt() {
        loadedAtVersion57();
        REDIS.admin().del(CatedraRedis.METADATA);
        synchronizationService.verifyCatalogVersion();
        REDIS.putMetadata(57, 50);

        assertThat(synchronizationService.verifyCatalogVersion()).isEqualTo(new VerificationResult.UpToDate(57));

        assertThat(tables.syncState())
                .containsEntry("local_version", 57L)
                .containsEntry("last_sync_type", "FULL")
                .containsEntry("last_sync_result", "SUCCESS")
                .containsEntry("last_error_type", null);
    }

    private void loadedAtVersion57() {
        assertThat(synchronizationService.verifyCatalogVersion()).isEqualTo(new VerificationResult.Updated(57));
    }

    private static Map<String, Object> row(List<Map<String, Object>> rows, long id) {
        return rows.stream().filter(row -> ((Long) row.get("id")) == id).findFirst().orElseThrow();
    }

    private static void assertNoSecrets(CapturedOutput output) {
        assertThat(output)
                .doesNotContain(FakeCatedraServer.TEST_TOKEN)
                .doesNotContain(FakeCatedraServer.TEST_PASSWORD)
                .doesNotContain(CatedraRedis.PASSWORD);
    }
}
