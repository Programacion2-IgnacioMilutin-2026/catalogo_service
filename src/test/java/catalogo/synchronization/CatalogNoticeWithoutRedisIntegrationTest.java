package catalogo.synchronization;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catedra.CatalogTopic;
import catalogo.catedra.CatedraRedis;
import catalogo.catedra.FakeCatedraServer;
import catalogo.catedra.application.service.TechnicalSessionService;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Un aviso con Redis caído.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class CatalogNoticeWithoutRedisIntegrationTest {

    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();
    private static final CatalogTopic TOPIC = CatalogTopic.random("notice-without-redis");

    static {
        TOPIC.create();
        CATEDRA.respondToLogin(FakeCatedraServer.loginWithRedisAndKafka("localhost", closedPort(),
                CatedraRedis.USERNAME, CatedraRedis.PASSWORD, TOPIC));
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
        registry.add("catalogo.catedra.redis.initial-delay", () -> "10ms");
        registry.add("catalogo.catedra.redis.max-delay", () -> "20ms");
        registry.add("catalogo.catedra.kafka.enabled", () -> "true");
    }

    @AfterAll
    static void stopCatedra() {
        CATEDRA.close();
    }

    @Test
    void aNoticeWithRedisDownIsRecordedAsAFailureNotRegisteredAndTheNextOneIsProcessed(CapturedOutput output) {
        CatalogTables tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        await().atMost(Duration.ofSeconds(10))
                .until(() -> technicalSessionService.getTechnicalSession().isPresent());
        await().atMost(Duration.ofSeconds(30)).until(() -> TOPIC.isAssignedTo(TOPIC.serviceConsumerGroup()));
        String first = UUID.randomUUID().toString();
        String next = UUID.randomUUID().toString();

        TOPIC.publishNotice(first, 57);
        TOPIC.publishNotice(next, 57);

        await().atMost(Duration.ofSeconds(60)).until(() -> output.getOut()
                .contains("Catalog update notice " + next + " finished"));
        await().atMost(Duration.ofSeconds(30))
                .until(() -> TOPIC.committedOffset(TOPIC.serviceConsumerGroup()) == 2);
        assertThat(output)
                .contains("Catalog update notice " + first + " finished: Failed[reason=SOURCE_UNAVAILABLE")
                .contains("Catalog update notice " + next + " finished: Failed[reason=SOURCE_UNAVAILABLE");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM processed_events WHERE event_id IN (?, ?)",
                Integer.class, first, next)).isZero();
        assertThat(tables.syncState())
                .containsEntry("local_version", 57L)
                .containsEntry("last_sync_type", "FULL")
                .containsEntry("last_sync_result", "ERROR")
                .containsEntry("last_error_type", "SOURCE_UNAVAILABLE");
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
