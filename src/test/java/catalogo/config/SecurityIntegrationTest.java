package catalogo.config;

import catalogo.TestTokens;
import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// La seguridad con la aplicación levantada, el servidor real y la base de test.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class SecurityIntegrationTest {

    private static final String PROFESSIONAL = "/api/professionals/15";
    private static final String UNAUTHORIZED_BODY = """
            {"title": "Unauthorized", "status": 401, "detail": "A valid bearer token is required",
             "instance": "/api/professionals/15"}
            """;

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
        tables.insertProfessional(15, 1, "Valentina", "Ibáñez", true);
        tables.initializeAt(57);

        String body = restTestClient.method(method).uri(path).exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(Objects.toString(body, "")).doesNotContain("Pediatría", "Valentina", "Ibáñez");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void everyRejectedTokenGetsTheSame401FromTheServer(String token, CapturedOutput output) {
        restTestClient.get().uri(PROFESSIONAL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().json(UNAUTHORIZED_BODY, JsonCompareMode.STRICT);

        assertNotLogged(output, token);
    }

    static Stream<Named<String>> everyRejectedTokenGetsTheSame401FromTheServer() {
        return Stream.of(
                Named.of("a text that is not a token", "xxx"),
                Named.of("expired", TestTokens.expired()),
                Named.of("changed after being signed", TestTokens.altered()),
                Named.of("signed with another key", TestTokens.signedWithOtherKey()));
    }

    @Test
    void withoutATokenTheServerAnswersTheSame401() {
        restTestClient.get().uri(PROFESSIONAL).exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody().json(UNAUTHORIZED_BODY, JsonCompareMode.STRICT);
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource
    void aPublicRouteAnswersTheSameWithAnyAuthorization(String authorization, String path, CapturedOutput output) {
        tables.insertCategory(1, "Pediatría", "Atención de niños", true);
        tables.insertProfessional(601, 1, "Valentina", "Ibáñez", true);
        tables.initializeAt(57);
        EntityExchangeResult<String> withoutHeader = restTestClient.get().uri(path).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult();

        restTestClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, authorization).exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody().json(Objects.requireNonNull(withoutHeader.getResponseBody()), JsonCompareMode.STRICT);

        assertNotLogged(output, authorization.substring(authorization.indexOf(' ') + 1));
    }

    static Stream<Arguments> aPublicRouteAnswersTheSameWithAnyAuthorization() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String path : new String[]{"/api/professional-categories", "/api/professionals"}) {
            cases.add(arguments(Named.of("a valid token", "Bearer " + TestTokens.valid()), path));
            cases.add(arguments(Named.of("an expired token", "Bearer " + TestTokens.expired()), path));
            cases.add(arguments(Named.of("an altered token", "Bearer " + TestTokens.altered()), path));
            cases.add(arguments(Named.of("a text that is not a token", "Bearer xxx"), path));
            cases.add(arguments(Named.of("another scheme", "Basic abc"), path));
        }
        return cases.build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/professionals", "/api/professional-categories"})
    void aPostWithAnInvalidTokenIsRejectedWith401(String path) {
        restTestClient.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer xxx").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/professionals", "/api/professional-categories"})
    void aPostWithAValidTokenIsNotAllowedOnARouteThatOnlyReads(String path, CapturedOutput output) {
        String token = TestTokens.valid();

        restTestClient.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                .expectStatus().isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        assertNotLogged(output, token);
    }

    // Solo se buscan los tokens con forma de JWT: un texto corto como "xxx" podría estar en cualquier otra línea.
    private static void assertNotLogged(CapturedOutput output, String credential) {
        if (credential.contains(".")) {
            assertThat(output).doesNotContain(credential);
        }
    }
}
