package catalogo.catalog;

import catalogo.TestTokens;
import catalogo.TestcontainersConfiguration;
import catalogo.catedra.FakeCatedraServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

// La búsqueda de profesionales con la aplicación levantada, la base de test y la cátedra simulada como testigo.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ProfessionalsIntegrationTest {

    private static final String PROFESSIONALS = "/api/professionals";
    private static final Duration WITHOUT_WAITING = Duration.ofSeconds(5);
    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();
    private static final String FOUR_PROFESSIONALS_JSON = """
            {
              "items": [
                {"id": 602, "firstName": "Tomás", "lastName": "Ferreyra", "enabled": false,
                 "category": {"id": 1, "name": "Kinesiología", "enabled": true},
                 "weeklySchedules": [
                   {"id": 703, "dayOfWeek": "TUESDAY", "startTime": "09:00:00", "endTime": "13:00:00",
                    "slotDurationMinutes": 20, "enabled": false}
                 ]},
                {"id": 601, "firstName": "Valentina", "lastName": "Ibáñez", "enabled": true,
                 "category": {"id": 1, "name": "Kinesiología", "enabled": true},
                 "weeklySchedules": [
                   {"id": 701, "dayOfWeek": "MONDAY", "startTime": "08:00:00", "endTime": "12:00:00",
                    "slotDurationMinutes": 30, "enabled": true},
                   {"id": 702, "dayOfWeek": "THURSDAY", "startTime": "14:00:00", "endTime": "18:00:00",
                    "slotDurationMinutes": 30, "enabled": true}
                 ]},
                {"id": 603, "firstName": "Lucía", "lastName": "Núñez", "enabled": true,
                 "category": {"id": 2, "name": "Nutrición", "enabled": false},
                 "weeklySchedules": [
                   {"id": 704, "dayOfWeek": "FRIDAY", "startTime": "10:00:00", "endTime": "14:00:00",
                    "slotDurationMinutes": 20, "enabled": true}
                 ]},
                {"id": 604, "firstName": "Mariana", "lastName": "Paz", "enabled": true,
                 "category": {"id": 2, "name": "Nutrición", "enabled": false},
                 "weeklySchedules": []}
              ],
              "page": 0,
              "size": 10,
              "totalItems": 4
            }""";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

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
        // El login del arranque corre en otro hilo: se espera para que no se cuente como un pedido de la búsqueda.
        await().atMost(Duration.ofSeconds(20)).until(() -> !CATEDRA.loginRequests().isEmpty());
    }

    @AfterEach
    void theTablesKeepTheirNames() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM catalog_sync_state", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM professionals", Integer.class)).isNotNull();
    }

    @Test
    void anInitializedCopyServesEveryProfessionalWithItsCategoryAndSchedulesWithoutAskingTheCatedra() {
        fourProfessionals();
        tables.initializeAt(57);
        int requestsBefore = CATEDRA.receivedRequests().size();

        restTestClient.get().uri(PROFESSIONALS).exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json(FOUR_PROFESSIONALS_JSON, JsonCompareMode.STRICT);

        assertThat(CATEDRA.receivedRequests()).hasSize(requestsBefore);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "categoryId=2 | 603 604",
            "enabled=false | 602",
            "name=nunez | 603",
            "name=FERREYRA | 602",
            "dayOfWeek=thursday&from=15:00 | 601",
            "dayOfWeek=TUESDAY | ''",
            "to=09:00 | 601",
            "dayOfWeek=MONDAY,FRIDAY | 601 603",
            "categoryId=1&enabled=true&dayOfWeek=MONDAY&dayOfWeek=THURSDAY | 601"})
    void eachFilterReachesTheSearch(String query, String expectedIds) {
        fourProfessionals();
        tables.initializeAt(57);
        List<Integer> expected = expectedIds.isBlank() ? List.of()
                : Arrays.stream(expectedIds.split(" ")).map(Integer::valueOf).toList();

        restTestClient.get().uri(PROFESSIONALS + "?" + query).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[*].id").isEqualTo(expected)
                .jsonPath("$.totalItems").isEqualTo(expected.size());
    }

    @Test
    void pagesComeInOrderWithTheRealTotalAlsoBeyondTheLastOne() {
        fourProfessionals();
        tables.initializeAt(57);

        assertPage("?size=2", List.of(602, 601), 0, 2);
        assertPage("?size=2&page=1", List.of(603, 604), 1, 2);
        assertPage("?size=2&page=2", List.of(), 2, 2);
        assertPage("?page=2000000000", List.of(), 2000000000, 10);
    }

    @Test
    void anAuthorizationHeaderIsIgnored() {
        fourProfessionals();
        tables.initializeAt(57);

        restTestClient.get().uri(PROFESSIONALS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.altered())
                .exchange()
                .expectStatus().isOk()
                .expectBody().json(FOUR_PROFESSIONALS_JSON, JsonCompareMode.STRICT);
    }

    @Test
    void emptyAndUnknownParametersGetTheSameResponseAsNoParameters() {
        fourProfessionals();
        tables.initializeAt(57);

        restTestClient.get().uri(PROFESSIONALS + "?name=&categoryId=&dayOfWeek=&page=&sort=lastName&foo=bar")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json(FOUR_PROFESSIONALS_JSON, JsonCompareMode.STRICT);
    }

    @Test
    void aCopyNotInitializedAnswers503WithoutAskingTheCatedraOrChangingTheBase() {
        assertNotInitializedAndNothingChanges();

        fourProfessionals();
        assertNotInitializedAndNothingChanges();
    }

    @Test
    void invalidParametersAnswer400BeforeLookingAtTheCopy() {
        fourProfessionals();

        restTestClient.get().uri(PROFESSIONALS + "?size=0&dayOfWeek=FUNDAY").exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.fieldErrors.length()").isEqualTo(2);
    }

    @Test
    void nothingMatchingGivesAnEmptyPageWithTotalZero() {
        tables.initializeAt(57);
        assertPage("", List.of(), 0, 10, 0);

        fourProfessionals();
        assertPage("?categoryId=99", List.of(), 0, 10, 0);
        assertPage("?name=gomez&enabled=true", List.of(), 0, 10, 0);
    }

    @Test
    void aFailureReadingTheSyncStateAnswers500WithoutInternalDetails(CapturedOutput output) {
        fourProfessionals();
        tables.initializeAt(57);

        assertInternalErrorWhileTheTableIsRenamed("catalog_sync_state", output);
    }

    @Test
    void aFailureReadingTheProfessionalsAnswers500WithoutInternalDetails(CapturedOutput output) {
        fourProfessionals();
        tables.initializeAt(57);

        assertInternalErrorWhileTheTableIsRenamed("professionals", output);
    }

    @Test
    void aSynchronizationNotYetConfirmedIsNotSeenAndDoesNotMakeTheSearchWait() throws Exception {
        fourProfessionals();
        tables.initializeAt(57);

        try (Connection synchronization = dataSource.getConnection()) {
            synchronization.setAutoCommit(false);
            try (Statement statement = synchronization.createStatement()) {
                statement.executeUpdate("INSERT INTO professionals (id, category_id, first_name, last_name, enabled) "
                        + "VALUES (605, 1, 'Ana', 'Acosta', true)");
                statement.executeUpdate("UPDATE weekly_schedules SET start_time = '07:00' WHERE id = 701");
            }

            assertTimeoutPreemptively(WITHOUT_WAITING, () -> restTestClient.get().uri(PROFESSIONALS).exchange()
                    .expectStatus().isOk()
                    .expectBody().json(FOUR_PROFESSIONALS_JSON, JsonCompareMode.STRICT));

            synchronization.commit();
        }

        restTestClient.get().uri(PROFESSIONALS).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[*].id").isEqualTo(List.of(605, 602, 601, 603, 604))
                .jsonPath("$.totalItems").isEqualTo(5)
                .jsonPath("$.items[2].weeklySchedules[0].startTime").isEqualTo("07:00:00");
    }

    // Valentina Ibáñez y Tomás Ferreyra en una categoría habilitada; Lucía Núñez y Mariana Paz, sin horarios, en una
    // deshabilitada. Los horarios se cargan desordenados.
    private void fourProfessionals() {
        tables.insertCategory(1, "Kinesiología", "Rehabilitación física", true);
        tables.insertCategory(2, "Nutrición", null, false);
        tables.insertProfessional(601, 1, "Valentina", "Ibáñez", true);
        tables.insertProfessional(602, 1, "Tomás", "Ferreyra", false);
        tables.insertProfessional(603, 2, "Lucía", "Núñez", true);
        tables.insertProfessional(604, 2, "Mariana", "Paz", true);
        tables.insertWeeklySchedule(702, 601, DayOfWeek.THURSDAY, LocalTime.of(14, 0), LocalTime.of(18, 0), 30, true);
        tables.insertWeeklySchedule(701, 601, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, true);
        tables.insertWeeklySchedule(703, 602, DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), 20, false);
        tables.insertWeeklySchedule(704, 603, DayOfWeek.FRIDAY, LocalTime.of(10, 0), LocalTime.of(14, 0), 20, true);
    }

    private void assertPage(String query, List<Integer> ids, int page, int size) {
        assertPage(query, ids, page, size, 4);
    }

    private void assertPage(String query, List<Integer> ids, int page, int size, int totalItems) {
        restTestClient.get().uri(PROFESSIONALS + query).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[*].id").isEqualTo(ids)
                .jsonPath("$.page").isEqualTo(page)
                .jsonPath("$.size").isEqualTo(size)
                .jsonPath("$.totalItems").isEqualTo(totalItems);
    }

    private void assertNotInitializedAndNothingChanges() {
        List<Map<String, Object>> professionalsBefore = tables.professionals();
        Map<String, Object> stateBefore = tables.syncState();
        int requestsBefore = CATEDRA.receivedRequests().size();

        restTestClient.get().uri(PROFESSIONALS).exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.code").isEqualTo("CATALOG_NOT_INITIALIZED");

        assertThat(tables.professionals()).isEqualTo(professionalsBefore);
        assertThat(tables.syncState()).isEqualTo(stateBefore);
        assertThat(CATEDRA.receivedRequests()).hasSize(requestsBefore);
    }

    private void assertInternalErrorWhileTheTableIsRenamed(String table, CapturedOutput output) {
        jdbcTemplate.execute("ALTER TABLE " + table + " RENAME TO " + table + "_hidden");
        try {
            byte[] body = restTestClient.get().uri(PROFESSIONALS).exchange()
                    .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(500)
                    .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                    .returnResult().getResponseBody();

            assertThat(new String(body, StandardCharsets.UTF_8))
                    .doesNotContain("catalog_sync_state", "_hidden", "relation", "does not exist", "SQL", "select",
                            "Exception");
            assertThat(output).contains("Unexpected error").contains("relation \"" + table + "\" does not exist");
        } finally {
            jdbcTemplate.execute("ALTER TABLE " + table + "_hidden RENAME TO " + table);
        }
    }
}
