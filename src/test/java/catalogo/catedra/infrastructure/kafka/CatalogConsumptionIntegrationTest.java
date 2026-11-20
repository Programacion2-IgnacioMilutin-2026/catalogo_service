package catalogo.catedra.infrastructure.kafka;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catedra.CatalogTopic;
import catalogo.catedra.CatedraRedis;
import catalogo.catedra.FakeCatedraServer;
import org.junit.jupiter.api.AfterAll;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// El consumo del topic del catálogo con la aplicación entera.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class CatalogConsumptionIntegrationTest {

    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();
    private static final CatedraRedis REDIS = CatedraRedis.start();
    private static final CatalogTopic TOPIC = CatalogTopic.random("consumption");
    private static final String BEFORE = "before-" + UUID.randomUUID();
    private static final String AFTER = "after-" + UUID.randomUUID();

    static {
        TOPIC.create();
        TOPIC.publishNotice(BEFORE, 57);
        REDIS.putMetadata(57, 50);
        REDIS.putSnapshotEntities();
        CATEDRA.respondToLogin(FakeCatedraServer.loginWithRedisAndKafka(REDIS.host(), REDIS.port(),
                CatedraRedis.USERNAME, CatedraRedis.PASSWORD, TOPIC));
        CATEDRA.respondToSnapshot(FakeCatedraServer.validSnapshot());
    }

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
    void theServiceConsumesWithItsOwnGroupAndOnlyTheNoticesPublishedAfterItStartedReading(CapturedOutput output) {
        new CatalogTables(jdbcTemplate).reset();
        await().atMost(Duration.ofSeconds(30)).until(() -> TOPIC.isAssignedTo(TOPIC.serviceConsumerGroup()));

        TOPIC.publishNotice(AFTER, 57);

        await().atMost(Duration.ofSeconds(30)).until(() -> processed(AFTER));
        await().atMost(Duration.ofSeconds(10)).until(() -> TOPIC.committedOffset(TOPIC.serviceConsumerGroup()) == 2);
        assertThat(processed(BEFORE)).isFalse();
        assertThat(TOPIC.isAssignedTo(TOPIC.suggestedConsumerGroup())).isFalse();
        assertThat(new CatalogTables(jdbcTemplate).syncState()).containsEntry("local_version", 57L);
        assertThat(output)
                .contains("Consuming the catalog topic " + TOPIC.topic() + " with consumer group "
                        + TOPIC.suggestedConsumerGroup() + "-catalog")
                .contains("Catalog update notice " + AFTER + " received: newVersion 57")
                .doesNotContain("Catalog update notice " + BEFORE);
    }

    private boolean processed(String eventId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class,
                eventId) == 1;
    }
}
