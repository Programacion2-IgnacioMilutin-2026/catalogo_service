package catalogo.config;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

// La seguridad con la aplicación levantada, el servidor real y la base de test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class SecurityIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CatalogTables tables;

    @BeforeEach
    void emptyCatalog() {
        tables = new CatalogTables(jdbcTemplate);
        tables.reset();
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET, /api/professionals/15, 401",
            "POST, /api/professionals, 403",
            "GET, /api/synchronization/status, 401",
            "POST, /api/professional-categories, 403"})
    void anyOtherRouteWithoutATokenIsRejectedWithoutData(HttpMethod method, String path, int status) {
        tables.insertCategory(1, "Pediatría", "Atención de niños", true);
        tables.initializeAt(57);

        String body = restTestClient.method(method).uri(path).exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(Objects.toString(body, "")).doesNotContain("Pediatría");
    }
}
