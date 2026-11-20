package catalogo.catalog;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// El listado con la base detenida responde 500 sin detalles internos.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "catalogo.catedra.base-url=",
        "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureRestTestClient
@DirtiesContext
@Timeout(120)
class ProfessionalCategoriesDatabaseDownIntegrationTest {

    private static final String CATEGORIES = "/api/professional-categories";
    private static final Duration WITHIN_THE_POOL_TIMEOUT = Duration.ofSeconds(10);

    // Su propia base y no la compartida, porque este test la detiene.
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

    static {
        POSTGRES.start();
    }

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void withTheDatabaseStoppedTheListAnswers500WithoutInternalDetails(CapturedOutput output) {
        CatalogTables tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        tables.insertCategory(1, "Pediatría", "Atención de niños", true);
        tables.initializeAt(57);
        restTestClient.get().uri(CATEGORIES).exchange().expectStatus().isOk();

        POSTGRES.stop();

        assertInternalErrorWithoutDetails();
        // Hikari entrega sin comprobar una conexión usada hace menos de 500 ms. Pasado ese margen, el pedido
        // siguiente comprueba las conexiones, las encuentra muertas y espera al pool hasta su tiempo.
        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).until(() -> true);
        assertInternalErrorWithoutDetails();
        assertThat(output).contains("Unexpected error").contains("Connection is not available, request timed out");
    }

    private void assertInternalErrorWithoutDetails() {
        long start = System.nanoTime();
        byte[] body = restTestClient.get().uri(CATEGORIES).exchange()
                .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .returnResult().getResponseBody();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(WITHIN_THE_POOL_TIMEOUT);
        assertThat(new String(body, StandardCharsets.UTF_8))
                .doesNotContain("Connection", "localhost", "jdbc", "refused", "Exception");
    }
}
