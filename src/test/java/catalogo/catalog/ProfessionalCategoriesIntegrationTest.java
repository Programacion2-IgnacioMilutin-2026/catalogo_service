package catalogo.catalog;

import catalogo.TestcontainersConfiguration;
import catalogo.catedra.FakeCatedraServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// El listado de categorías con la aplicación levantada, la base de test y la cátedra simulada.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ProfessionalCategoriesIntegrationTest {

    private static final String CATEGORIES = "/api/professional-categories";
    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CatalogTables tables;

    @DynamicPropertySource
    static void catedraProperties(DynamicPropertyRegistry registry) {
        registry.add("catalogo.catedra.base-url", CATEDRA::baseUrl);
        registry.add("catalogo.catedra.username", () -> FakeCatedraServer.TEST_USERNAME);
        registry.add("catalogo.catedra.password", () -> FakeCatedraServer.TEST_PASSWORD);
    }

    @AfterAll
    static void stopCatedra() {
        CATEDRA.close();
    }

    @BeforeEach
    void emptyCatalogAfterTheStartupLogin() {
        tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        await().atMost(Duration.ofSeconds(20)).until(() -> !CATEDRA.loginRequests().isEmpty());
    }

    @Test
    void anInitializedCopyListsEveryCategoryWithItsFields() {
        tables.insertCategory(1, "Pediatría", "Atención de niños", true);
        tables.insertCategory(2, "Kinesiología", "Rehabilitación física", false);
        tables.initializeAt(57);

        restTestClient.get().uri(CATEGORIES).exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("""
                        [
                          {"id": 2, "name": "Kinesiología", "description": "Rehabilitación física", "enabled": false},
                          {"id": 1, "name": "Pediatría", "description": "Atención de niños", "enabled": true}
                        ]""", JsonCompareMode.STRICT);
    }

    @Test
    void listingReadsOnlyTheLocalCopyWithoutCallingTheCatedra() {
        tables.insertCategory(1, "Pediatría", "Atención de niños", true);
        tables.initializeAt(57);
        int requestsBefore = CATEDRA.receivedRequests().size();

        restTestClient.get().uri(CATEGORIES).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].name").isEqualTo("Pediatría");

        assertThat(CATEDRA.receivedRequests()).hasSize(requestsBefore);
    }

    @Test
    void theResponseComesInSpanishAlphabeticalOrderWithTiesById() {
        tables.insertCategory(1, "pediatría", null, true);
        tables.insertCategory(2, "Ñandú", null, true);
        tables.insertCategory(3, "Óptica", null, false);
        tables.insertCategory(4, "Nutrición", null, true);
        tables.insertCategory(5, "optica", null, true);
        tables.initializeAt(57);

        restTestClient.get().uri(CATEGORIES).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[*].id").isEqualTo(List.of(4, 2, 3, 5, 1));
    }

    @Test
    void withoutALocalVersionButWithCategoriesItAnswers503AndChangesNothing() {
        tables.insertCategory(1, "Pediatría", "Atención de niños", true);

        assertNotInitializedAndNothingChanges();
    }

    @Test
    void withoutALocalVersionAndWithoutCategoriesItAnswers503AndChangesNothing() {
        assertNotInitializedAndNothingChanges();
    }

    @Test
    void anInitializedCopyWithoutCategoriesAnswersAnEmptyList() {
        tables.initializeAt(57);

        restTestClient.get().uri(CATEGORIES).exchange()
                .expectStatus().isOk()
                .expectBody().json("[]", JsonCompareMode.STRICT);
    }

    private void assertNotInitializedAndNothingChanges() {
        List<Map<String, Object>> categoriesBefore = tables.categories();
        Map<String, Object> stateBefore = tables.syncState();
        int requestsBefore = CATEDRA.receivedRequests().size();

        restTestClient.get().uri(CATEGORIES).exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.code").isEqualTo("CATALOG_NOT_INITIALIZED");

        assertThat(tables.categories()).isEqualTo(categoriesBefore);
        assertThat(tables.syncState()).isEqualTo(stateBefore);
        assertThat(CATEDRA.receivedRequests()).hasSize(requestsBefore);
    }
}
