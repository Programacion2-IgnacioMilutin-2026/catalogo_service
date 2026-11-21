package catalogo.catalog;

import catalogo.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

// El listado con una tabla rota a propósito y con una sincronización todavía sin confirmar.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "catalogo.catedra.base-url=")
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ProfessionalCategoriesDatabaseIntegrationTest {

    private static final String CATEGORIES = "/api/professional-categories";
    private static final Duration WITHOUT_WAITING = Duration.ofSeconds(5);

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void copyWithOneCategory() {
        CatalogTables tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        tables.insertCategory(1, "Pediatría", "Atención de niños", true);
        tables.initializeAt(57);
    }

    @AfterEach
    void theTablesKeepTheirNames() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM catalog_sync_state", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM professional_categories", Integer.class))
                .isNotNull();
    }

    @Test
    void aFailureReadingTheSyncStateAnswers500WithoutInternalDetails(CapturedOutput output) {
        assertInternalErrorWhileTheTableIsRenamed("catalog_sync_state", output);
    }

    @Test
    void aFailureReadingTheCategoriesAnswers500WithoutInternalDetails(CapturedOutput output) {
        assertInternalErrorWhileTheTableIsRenamed("professional_categories", output);
    }

    @Test
    void aSynchronizationNotYetConfirmedIsNotSeenAndDoesNotMakeTheListWait() throws Exception {
        try (Connection synchronization = dataSource.getConnection()) {
            synchronization.setAutoCommit(false);
            try (Statement statement = synchronization.createStatement()) {
                statement.executeUpdate("UPDATE professional_categories SET name = 'Zoología' WHERE id = 1");
                statement.executeUpdate("INSERT INTO professional_categories (id, name, description, enabled) "
                        + "VALUES (2, 'Cardiología', NULL, true)");
            }

            assertTimeoutPreemptively(WITHOUT_WAITING, () -> restTestClient.get().uri(CATEGORIES).exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$[*].name").isEqualTo(List.of("Pediatría")));

            synchronization.commit();
        }

        restTestClient.get().uri(CATEGORIES).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[*].name").isEqualTo(List.of("Cardiología", "Zoología"));
    }

    private void assertInternalErrorWhileTheTableIsRenamed(String table, CapturedOutput output) {
        jdbcTemplate.execute("ALTER TABLE " + table + " RENAME TO " + table + "_hidden");
        try {
            byte[] body = restTestClient.get().uri(CATEGORIES).exchange()
                    .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(500)
                    .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                    .returnResult().getResponseBody();

            assertThat(new String(body, StandardCharsets.UTF_8))
                    .doesNotContain(table, "relation", "does not exist", "SQL", "select", "Exception");
            assertThat(output).contains("Unexpected error").contains("relation \"" + table + "\" does not exist");
        } finally {
            jdbcTemplate.execute("ALTER TABLE " + table + "_hidden RENAME TO " + table);
        }
    }
}
