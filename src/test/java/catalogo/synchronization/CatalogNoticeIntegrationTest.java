package catalogo.synchronization;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catedra.CatalogTopic;
import catalogo.catedra.CatedraRedis;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Un aviso de Kafka de punta a punta, con la aplicación entera.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class CatalogNoticeIntegrationTest {

    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();
    private static final CatedraRedis REDIS = CatedraRedis.start();
    private static final CatalogTopic TOPIC = CatalogTopic.random("notice");

    static {
        TOPIC.create();
        CATEDRA.respondToLogin(FakeCatedraServer.loginWithRedisAndKafka(REDIS.host(), REDIS.port(),
                CatedraRedis.USERNAME, CatedraRedis.PASSWORD, TOPIC));
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
        registry.add("catalogo.catedra.kafka.enabled", () -> "true");
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
        await().atMost(Duration.ofSeconds(30)).until(() -> TOPIC.isAssignedTo(TOPIC.serviceConsumerGroup()));
    }

    @Test
    void aNoticeWithPendingVersionsAppliesThemIncrementallyAndIsRegistered(CapturedOutput output) {
        loadedAtVersion57();
        int snapshotsBefore = CATEDRA.snapshotRequests().size();
        ObjectNode renamed = CatedraRedis.snapshotEntity("professionals", 601);
        renamed.put("lastName", "Ibáñez Pérez");
        REDIS.putEntity(CatedraRedis.PROFESSIONALS, 601, renamed.toString());
        ObjectNode disabled = CatedraRedis.snapshotEntity("professionalCategories", 502);
        disabled.put("enabled", false);
        REDIS.putEntity(CatedraRedis.CATEGORIES, 502, disabled.toString());
        REDIS.putChanges(58, List.of(), List.of(601L), List.of());
        REDIS.putChanges(59, List.of(502L), List.of(), List.of());
        REDIS.putMetadata(59, 50);
        String eventId = newEventId();

        TOPIC.publishNotice(eventId, 59);

        await().atMost(Duration.ofSeconds(30)).until(() -> processed(eventId));
        assertThat(row(tables.professionals(), 601)).containsEntry("last_name", "Ibáñez Pérez");
        assertThat(row(tables.categories(), 502)).containsEntry("enabled", false);
        assertThat(tables.syncState())
                .containsEntry("local_version", 59L)
                .containsEntry("last_sync_type", "INCREMENTAL")
                .containsEntry("last_sync_result", "SUCCESS");
        assertThat(CATEDRA.snapshotRequests()).hasSize(snapshotsBefore);
        awaitAllConfirmed();
        assertThat(output)
                .contains("Catalog update notice " + eventId + " received: newVersion 59")
                .contains("Catalog update notice " + eventId + " applied versions 58 to 59: local version 59")
                .contains("Catalog update notice " + eventId + " finished: Updated[localVersion=59]");
        assertNoSecrets(output);
    }

    @Test
    void anInvalidNoticeIsDiscardedAndTheNextOneIsProcessed(CapturedOutput output) {
        String invalid = newEventId();
        String valid = newEventId();

        TOPIC.publish("57", CatalogTopic.notice(invalid, 57).replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
        TOPIC.publishNotice(valid, 57);

        await().atMost(Duration.ofSeconds(30)).until(() -> processed(valid));
        awaitAllConfirmed();
        assertThat(processed(invalid)).isFalse();
        assertThat(output)
                .contains("discarded (eventId=" + invalid + "): unsupported schemaVersion 2")
                .doesNotContain("Catalog update notice " + invalid);
        assertNoSecrets(output);
    }

    @Test
    void theSameNoticeTwiceDoesNotRepeatAnything(CapturedOutput output) {
        loadedAtVersion57();
        String eventId = newEventId();
        TOPIC.publishNotice(eventId, 57);
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(eventId));
        Map<String, Object> stateAfterTheFirst = tables.syncState();

        TOPIC.publishNotice(eventId, 57);

        await().atMost(Duration.ofSeconds(30)).until(() -> occurrences(output,
                "Catalog update notice " + eventId + " was already processed: nothing to do") == 1);
        awaitAllConfirmed();
        assertThat(occurrences(output, "Catalog update notice " + eventId + " received")).isEqualTo(2);
        assertThat(occurrences(output, "Catalog update notice " + eventId + ": up to date at version 57"))
                .isEqualTo(1);
        assertThat(tables.syncState()).isEqualTo(stateAfterTheFirst);
    }

    @Test
    void aNoticeDeliveredAgainAfterACutFindsTheCatalogUpToDateWithoutRepeatingChanges(CapturedOutput output) {
        loadedAtVersion57();
        ObjectNode renamed = CatedraRedis.snapshotEntity("professionals", 601);
        renamed.put("lastName", "Ibáñez Pérez");
        REDIS.putEntity(CatedraRedis.PROFESSIONALS, 601, renamed.toString());
        REDIS.putChanges(58, List.of(), List.of(601L), List.of());
        REDIS.putMetadata(58, 50);
        String eventId = newEventId();
        TOPIC.publishNotice(eventId, 58);
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(eventId));
        Map<String, Object> stateAfterApplying = tables.syncState();

        jdbcTemplate.update("DELETE FROM processed_events WHERE event_id = ?", eventId);
        TOPIC.publishNotice(eventId, 58);

        await().atMost(Duration.ofSeconds(30)).until(() -> processed(eventId));
        await().atMost(Duration.ofSeconds(10)).until(() -> occurrences(output,
                "Catalog update notice " + eventId + " finished: UpToDate[localVersion=58]") == 1);
        assertThat(occurrences(output, "Catalog update notice " + eventId + " applied versions 58 to 58"))
                .isEqualTo(1);
        assertThat(tables.syncState())
                .containsEntry("local_version", 58L)
                .containsEntry("last_successful_sync_at", stateAfterApplying.get("last_successful_sync_at"));
        assertThat(row(tables.professionals(), 601)).containsEntry("last_name", "Ibáñez Pérez");
    }

    @Test
    void aNoticeWithALineBreakInItsEventIdReachesTheLogWithoutIt(CapturedOutput output) {
        String forged = "fake\\n2026-11-20 ERROR forged line";

        TOPIC.publish("57", CatalogTopic.notice(forged, 57));

        awaitAllConfirmed();
        assertThat(output)
                .contains("discarded (eventId=fake?2026-11-20 ERROR forged line): eventId with control characters")
                .doesNotContain("fake\n2026-11-20");
    }

    private void loadedAtVersion57() {
        assertThat(synchronizationService.synchronizeFullCatalog()).isEqualTo(new FullSyncResult.Applied(57));
    }

    private void awaitAllConfirmed() {
        await().atMost(Duration.ofSeconds(30))
                .until(() -> TOPIC.committedOffset(TOPIC.serviceConsumerGroup()) == TOPIC.endOffset());
    }

    private boolean processed(String eventId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class,
                eventId) == 1;
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }

    private static long occurrences(CapturedOutput output, String text) {
        return output.getOut().split(java.util.regex.Pattern.quote(text), -1).length - 1L;
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
