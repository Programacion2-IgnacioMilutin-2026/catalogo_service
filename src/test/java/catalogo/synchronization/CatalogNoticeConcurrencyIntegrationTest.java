package catalogo.synchronization;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catedra.CatalogTopic;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Un aviso de Kafka junto con otra sincronización, y con la base rechazando su registro.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class CatalogNoticeConcurrencyIntegrationTest {

    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();
    private static final CatedraRedis REDIS = CatedraRedis.start();
    private static final CatalogTopic TOPIC = CatalogTopic.random("notice-concurrency");
    private static final Duration SLOW_SNAPSHOT = Duration.ofSeconds(3);

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
        CATEDRA.respondToSnapshot(FakeCatedraServer.validSnapshot().after(SLOW_SNAPSHOT));
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
    void aNoticeWaitsForAVerificationInProgressAndReadsAgainAfterIt(CapturedOutput output)
            throws InterruptedException {
        int snapshotsBefore = CATEDRA.snapshotRequests().size();
        AtomicReference<VerificationResult> verification = new AtomicReference<>();
        Thread verifying = Thread.ofVirtual().start(() ->
                verification.set(synchronizationService.verifyCatalogVersion()));
        await().atMost(Duration.ofSeconds(10)).until(() -> CATEDRA.snapshotRequests().size() == snapshotsBefore + 1);
        String eventId = UUID.randomUUID().toString();

        TOPIC.publishNotice(eventId, 57);

        await().atMost(Duration.ofSeconds(10)).until(() -> output.getOut()
                .contains("Catalog update notice " + eventId + " received"));
        assertThat(processed(eventId)).isFalse();
        verifying.join(Duration.ofSeconds(30));
        assertThat(verification.get()).isEqualTo(new VerificationResult.Updated(57));
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(eventId));
        String log = output.getOut();
        assertThat(log.indexOf("Catalog update notice " + eventId + ": up to date at version 57"))
                .isGreaterThan(log.indexOf("Catalog verification: up to date at version 57"));
        assertThat(log).contains("Catalog update notice " + eventId + " finished: UpToDate[localVersion=57]");
        assertThat(CATEDRA.snapshotRequests()).hasSize(snapshotsBefore + 1);
    }

    @Test
    void aVerificationWhileANoticeIsBeingProcessedIsSkipped(CapturedOutput output) {
        int snapshotsBefore = CATEDRA.snapshotRequests().size();
        String eventId = UUID.randomUUID().toString();

        TOPIC.publishNotice(eventId, 57);
        await().atMost(Duration.ofSeconds(10)).until(() -> CATEDRA.snapshotRequests().size() == snapshotsBefore + 1);

        assertThat(synchronizationService.verifyCatalogVersion()).isEqualTo(new VerificationResult.Skipped());
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(eventId));
        assertThat(output)
                .contains("Catalog verification skipped: another synchronization is in progress")
                .contains("Catalog update notice " + eventId + " finished: Updated[localVersion=57]");
        assertThat(CATEDRA.snapshotRequests()).hasSize(snapshotsBefore + 1);
    }

    @Test
    void aRegistrationRejectedByTheDatabaseIsLoggedAndTheNoticeConfirmed(CapturedOutput output) {
        CATEDRA.respondToSnapshot(FakeCatedraServer.validSnapshot());
        String eventId = "rejected-" + UUID.randomUUID();
        jdbcTemplate.execute("CREATE FUNCTION fail_on_rejected_notice() RETURNS trigger AS $$ BEGIN "
                + "IF NEW.event_id LIKE 'rejected-%' THEN RAISE EXCEPTION 'forced failure'; END IF; RETURN NEW; END; "
                + "$$ LANGUAGE plpgsql");
        jdbcTemplate.execute("CREATE TRIGGER fail_on_rejected_notice BEFORE INSERT ON processed_events "
                + "FOR EACH ROW EXECUTE FUNCTION fail_on_rejected_notice()");
        try {
            TOPIC.publishNotice(eventId, 57);

            await().atMost(Duration.ofSeconds(30)).until(() -> output.getOut()
                    .contains("Catalog update notice " + eventId + " finished"));
            await().atMost(Duration.ofSeconds(30))
                    .until(() -> TOPIC.committedOffset(TOPIC.serviceConsumerGroup()) == TOPIC.endOffset());
            assertThat(processed(eventId)).isFalse();
            assertThat(output)
                    .contains("Catalog update notice " + eventId + " could not be registered as processed")
                    .contains("Catalog update notice " + eventId + " finished: Updated[localVersion=57]");
            assertThat(tables.syncState())
                    .containsEntry("local_version", 57L)
                    .containsEntry("last_sync_result", "SUCCESS")
                    .containsEntry("last_error_type", null);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER fail_on_rejected_notice ON processed_events");
            jdbcTemplate.execute("DROP FUNCTION fail_on_rejected_notice()");
        }
    }

    private boolean processed(String eventId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class,
                eventId) == 1;
    }
}
