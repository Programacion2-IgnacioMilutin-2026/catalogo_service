package catalogo.synchronization;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catedra.CatedraRedis;
import catalogo.catedra.FakeCatedraServer;
import catalogo.catedra.application.service.TechnicalSessionService;
import catalogo.synchronization.application.service.SynchronizationService;
import catalogo.synchronization.domain.model.VerificationResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Con la base vacía y Redis inalcanzable, el catálogo se carga igual con el snapshot y queda registrado que Redis no respondió.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CatalogVerificationWithoutRedisIntegrationTest {

    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();

    static {
        CATEDRA.respondToLogin(FakeCatedraServer.loginWithRedis("localhost", closedPort(), CatedraRedis.USERNAME,
                CatedraRedis.PASSWORD));
    }

    @Autowired
    private SynchronizationService synchronizationService;

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
    }

    @AfterAll
    static void stopCatedra() {
        CATEDRA.close();
    }

    @Test
    void theSnapshotIsLoadedAndTheUnavailableRedisIsRecorded() {
        CatalogTables tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        await().atMost(Duration.ofSeconds(10))
                .until(() -> technicalSessionService.getTechnicalSession().isPresent());

        VerificationResult result = synchronizationService.verifyCatalogVersion();

        assertThat(((VerificationResult.Failed) result).reason()).startsWith("SOURCE_UNAVAILABLE");
        assertThat(tables.ids("professionals")).containsExactly(601L, 602L, 603L);
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
