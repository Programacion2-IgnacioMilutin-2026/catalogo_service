package catalogo.config;

import catalogo.TestTokens;
import catalogo.catalog.CatalogTables;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// El servicio sin la clave pública de turnos.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "catalogo.jwt.public-key-location=")
@AutoConfigureRestTestClient
@DirtiesContext
class SecurityWithoutKeyIntegrationTest {

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
    void withoutTheKeyTheServiceStartsWarnsAndRejectsEveryTokenOnProtectedRoutes(CapturedOutput output) {
        CatalogTables tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        tables.insertCategory(1, "Pediatría", "Atención de niños", true);
        tables.initializeAt(57);

        restTestClient.get().uri("/api/professionals/15")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid()).exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        restTestClient.get().uri("/api/professional-categories").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[*].name").isEqualTo(List.of("Pediatría"));

        assertThat(output).contains("The JWT public key is not configured")
                .contains("every request to a protected route will be rejected")
                .doesNotContain("BEGIN");
    }
}
