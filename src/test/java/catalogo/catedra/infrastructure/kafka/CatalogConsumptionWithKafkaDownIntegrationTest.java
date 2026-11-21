package catalogo.catedra.infrastructure.kafka;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catalog.domain.ports.in.GetCatalogSyncStateUseCase;
import catalogo.catedra.CatalogTopic;
import catalogo.catedra.CatedraKafka;
import catalogo.catedra.CatedraRedis;
import catalogo.catedra.FakeCatedraServer;
import catalogo.catedra.application.service.TechnicalSessionService;
import catalogo.catedra.domain.model.TechnicalIntegration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Un consumo que arranca con Kafka sin responder.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class CatalogConsumptionWithKafkaDownIntegrationTest {

    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();
    private static final CatedraRedis REDIS = CatedraRedis.start();
    private static final CatalogTopic TOPIC = CatalogTopic.random("kafka-down");
    private static final String FIRST = "first-" + UUID.randomUUID();
    private static final String PENDING = "pending-" + UUID.randomUUID();

    static {
        TOPIC.create();
        REDIS.putMetadata(57, 50);
        REDIS.putSnapshotEntities();
        CATEDRA.respondToLogin(FakeCatedraServer.loginWithRedisAndKafka(REDIS.host(), REDIS.port(),
                CatedraRedis.USERNAME, CatedraRedis.PASSWORD, TOPIC));
        CATEDRA.respondToSnapshot(FakeCatedraServer.validSnapshot());
    }

    @Autowired
    private KafkaCatalogConsumer serviceConsumer;

    @Autowired
    private CatedraKafkaProperties catedraKafkaProperties;

    @Autowired
    private TechnicalSessionService technicalSessionService;

    @Autowired
    private GetCatalogSyncStateUseCase getCatalogSyncStateUseCase;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void aConsumptionStartedWithKafkaDownResumesFromTheCommittedPositionWhenKafkaReturns(CapturedOutput output)
            throws InterruptedException {
        new CatalogTables(jdbcTemplate).reset();
        await().atMost(Duration.ofSeconds(30)).until(() -> TOPIC.isAssignedTo(TOPIC.serviceConsumerGroup()));
        TOPIC.publishNotice(FIRST, 57);
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(FIRST));
        await().atMost(Duration.ofSeconds(10)).until(() -> TOPIC.committedOffset(TOPIC.serviceConsumerGroup()) == 1);

        serviceConsumer.destroy();
        TOPIC.publishNotice(PENDING, 57);
        Map<String, Object> stateBefore = new CatalogTables(jdbcTemplate).syncState();
        TechnicalIntegration integration = technicalSessionService.getTechnicalSession().orElseThrow().integration();
        KafkaCatalogConsumer restarted = new KafkaCatalogConsumer(catedraKafkaProperties,
                applicationContext.getBeanProvider(CatalogUpdateListener.class));
        CatedraKafka.pause();
        try {
            restarted.integrationEnabled(integration);
            Thread.sleep(5000);

            assertThat(processed(PENDING)).isFalse();
            assertThat(getCatalogSyncStateUseCase.getCatalogSyncState().getLocalVersion()).isEqualTo(57L);
            assertThat(new CatalogTables(jdbcTemplate).syncState()).isEqualTo(stateBefore);
        } finally {
            CatedraKafka.unpause();
        }

        try {
            await().atMost(Duration.ofSeconds(90)).until(() -> processed(PENDING));
            assertThat(output).contains("Catalog update notice " + PENDING + " finished: UpToDate[localVersion=57]");
        } finally {
            restarted.destroy();
        }
    }

    private boolean processed(String eventId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class,
                eventId) == 1;
    }
}
