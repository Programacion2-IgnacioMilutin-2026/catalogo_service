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

// El disparador encendido con un intervalo corto.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "catalogo.synchronization.verification.enabled=true",
        "catalogo.synchronization.verification.interval=300ms"})
@Import(TestcontainersConfiguration.class)
class VerificationSchedulingIntegrationTest {

    private static final String UP_TO_DATE = "Catalog verification: up to date at version 57";
    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();
    private static final CatedraRedis REDIS = CatedraRedis.start();

    static {
        REDIS.putMetadata(57, 50);
        REDIS.putSnapshotEntities();
        CATEDRA.respondToLogin(FakeCatedraServer.loginWithRedis(REDIS.host(), REDIS.port(), CatedraRedis.USERNAME,
                CatedraRedis.PASSWORD));
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
    void theVerificationRepeatsWithTheConfiguredInterval(CapturedOutput output) {
        await().atMost(Duration.ofSeconds(20))
                .until(() -> new CatalogTables(jdbcTemplate).syncState().get("local_version") != null);
        int before = occurrences(output.getOut(), UP_TO_DATE);

        await().atMost(Duration.ofSeconds(10)).until(() -> occurrences(output.getOut(), UP_TO_DATE) >= before + 3);

        assertThat(new CatalogTables(jdbcTemplate).syncState()).containsEntry("local_version", 57L);
    }

    @Test
    void aSlowVerificationDoesNotOverlapWithTheNextOne(CapturedOutput output) {
        await().atMost(Duration.ofSeconds(20)).until(() -> occurrences(output.getOut(), UP_TO_DATE) >= 1);

        REDIS.pauseClients(Duration.ofMillis(1500));
        int afterPause = occurrences(output.getOut(), UP_TO_DATE);
        await().atMost(Duration.ofSeconds(10)).until(() -> occurrences(output.getOut(), UP_TO_DATE) >= afterPause + 2);

        assertThat(output.getOut()).doesNotContain("Catalog verification skipped");
    }

    private static int occurrences(String text, String fragment) {
        int count = 0;
        for (int index = text.indexOf(fragment); index >= 0; index = text.indexOf(fragment, index + 1)) {
            count++;
        }
        return count;
    }
}
