package catalogo.synchronization;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// La verificación del arranque espera al login inicial demorado y después carga el catálogo sin frenar el arranque.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = "catalogo.synchronization.verification.enabled=true")
@Import(TestcontainersConfiguration.class)
class VerificationAtStartupIntegrationTest {

    private static final Duration LOGIN_DELAY = Duration.ofMillis(1500);
    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();
    private static final CatedraRedis REDIS = CatedraRedis.start();

    static {
        REDIS.putMetadata(57, 50);
        REDIS.putSnapshotEntities();
        CATEDRA.respondToLogin(FakeCatedraServer.loginWithRedis(REDIS.host(), REDIS.port(), CatedraRedis.USERNAME,
                CatedraRedis.PASSWORD).after(LOGIN_DELAY));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void catedraProperties(DynamicPropertyRegistry registry) {
        registry.add("catalogo.catedra.base-url", CATEDRA::baseUrl);
        registry.add("catalogo.catedra.username", () -> FakeCatedraServer.TEST_USERNAME);
        registry.add("catalogo.catedra.password", () -> FakeCatedraServer.TEST_PASSWORD);
    }

    @AfterAll
    static void stopCatedra() {
        CATEDRA.close();
        REDIS.close();
    }

    @Test
    void theStartupVerificationWaitsForTheInitialLoginAndThenLoadsTheCatalog(CapturedOutput output) {
        assertThat(new CatalogTables(jdbcTemplate).syncState()).containsEntry("local_version", null);

        await().atMost(Duration.ofSeconds(20))
                .until(() -> new CatalogTables(jdbcTemplate).syncState().get("local_version") != null);

        assertThat(new CatalogTables(jdbcTemplate).syncState())
                .containsEntry("local_version", 57L)
                .containsEntry("last_sync_type", "FULL")
                .containsEntry("last_sync_result", "SUCCESS");
        assertThat(CATEDRA.loginRequests()).hasSize(1);
        assertThat(output)
                .contains("Waiting for the initial technical login")
                .contains("Catalog verification: no local version, requesting the full synchronization")
                .doesNotContain("Catalog snapshot not requested: the technical integration is not available")
                .doesNotContain(FakeCatedraServer.TEST_TOKEN)
                .doesNotContain(CatedraRedis.PASSWORD);
    }
}
