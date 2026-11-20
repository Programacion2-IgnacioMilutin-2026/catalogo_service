package catalogo.catedra.infrastructure.kafka;

import catalogo.TestcontainersConfiguration;
import catalogo.catedra.CatalogTopic;
import catalogo.catedra.CatedraRedis;
import catalogo.catedra.FakeCatedraServer;
import catalogo.catedra.application.service.TechnicalSessionService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Si el login del arranque deja la sesión degradada, el consumo no arranca.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class CatalogConsumptionAfterDegradedLoginIntegrationTest {

    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();
    private static final CatedraRedis REDIS = CatedraRedis.start();
    private static final CatalogTopic TOPIC = CatalogTopic.random("degraded-login");

    static {
        TOPIC.create();
        REDIS.putMetadata(57, 50);
        REDIS.putSnapshotEntities();
        CATEDRA.respondToLogin(FakeCatedraServer.problem(503, "SERVICE_UNAVAILABLE"),
                FakeCatedraServer.problem(503, "SERVICE_UNAVAILABLE"),
                FakeCatedraServer.loginWithRedisAndKafka(REDIS.host(), REDIS.port(), CatedraRedis.USERNAME,
                        CatedraRedis.PASSWORD, TOPIC));
        CATEDRA.respondToSnapshot(FakeCatedraServer.validSnapshot());
    }

    @Autowired
    private TechnicalSessionService technicalSessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void catedraProperties(DynamicPropertyRegistry registry) {
        registry.add("catalogo.catedra.base-url", CATEDRA::baseUrl);
        registry.add("catalogo.catedra.username", () -> FakeCatedraServer.TEST_USERNAME);
        registry.add("catalogo.catedra.password", () -> FakeCatedraServer.TEST_PASSWORD);
        registry.add("catalogo.catedra.login.max-attempts", () -> "2");
        registry.add("catalogo.catedra.login.initial-delay", () -> "10ms");
        registry.add("catalogo.catedra.login.min-interval", () -> "1s");
        registry.add("catalogo.catedra.kafka.enabled", () -> "true");
    }

    @AfterAll
    static void stopCatedraAndRedis() {
        CATEDRA.close();
        REDIS.close();
    }

    @Test
    void aRenewalThatEnablesTheSessionStartsTheConsumption() throws InterruptedException {
        await().atMost(Duration.ofSeconds(10)).until(() -> CATEDRA.loginRequests().size() == 2);
        Thread.sleep(2000);
        assertThat(technicalSessionService.getTechnicalSession()).isEmpty();
        assertThat(TOPIC.isAssignedTo(TOPIC.serviceConsumerGroup())).isFalse();

        assertThat(technicalSessionService.renewTechnicalSession(null)).isPresent();

        await().atMost(Duration.ofSeconds(30)).until(() -> TOPIC.isAssignedTo(TOPIC.serviceConsumerGroup()));
        String eventId = "after-renewal-" + UUID.randomUUID();
        TOPIC.publishNotice(eventId, 57);
        await().atMost(Duration.ofSeconds(30)).until(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId) == 1);
    }
}
