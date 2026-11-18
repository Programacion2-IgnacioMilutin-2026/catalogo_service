package catalogo.catedra.infrastructure.client;

import catalogo.FakeHttpServer.ReceivedRequest;
import catalogo.FakeHttpServer.Response;
import catalogo.catedra.FakeCatedraServer;
import catalogo.catedra.TechnicalIntegrations;
import catalogo.catedra.domain.model.ProvisioningStatus;
import catalogo.catedra.domain.model.TechnicalIntegration;
import catalogo.catedra.domain.model.TechnicalLoginResult;
import catalogo.catedra.infrastructure.client.dto.IntegrationResponse;
import catalogo.catedra.infrastructure.client.mapper.TechnicalIntegrationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class CatedraLoginClientAdapterTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);
    private static final CatedraProperties.Login LOGIN = new CatedraProperties.Login(
            5, Duration.ofSeconds(1), 2, Duration.ofSeconds(30), Duration.ofSeconds(60));
    private static final CatedraProperties.Snapshot SNAPSHOT = new CatedraProperties.Snapshot(
            CONNECT_TIMEOUT, READ_TIMEOUT, 5, Duration.ofSeconds(1), 2, Duration.ofSeconds(30));
    private static final CatedraProperties.Redis REDIS = new CatedraProperties.Redis(
            CONNECT_TIMEOUT, READ_TIMEOUT, 3, Duration.ofSeconds(1), 2, Duration.ofSeconds(30));

    private final FakeCatedraServer catedra = FakeCatedraServer.start();

    @AfterEach
    void stopCatedra() {
        catedra.close();
    }

    @Test
    void sendsTheConfiguredCredentialsWithRememberMe() {
        adapter().login();

        assertThat(catedra.loginRequests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.header("Content-Type")).startsWith("application/json");
            JsonNode body = JsonMapper.builder().build().readTree(request.body());
            assertThat(body.get("username").asString()).isEqualTo(FakeCatedraServer.TEST_USERNAME);
            assertThat(body.get("password").asString()).isEqualTo(FakeCatedraServer.TEST_PASSWORD);
            assertThat(body.get("rememberMe").asBoolean()).isTrue();
        });
    }

    @Test
    void aProvisionedResponseIsASuccessWithTheTokenAndTheDataThisServiceUses() {
        TechnicalLoginResult result = adapter().login();

        assertThat(result).isEqualTo(
                new TechnicalLoginResult.Success(FakeCatedraServer.TEST_TOKEN, TechnicalIntegrations.complete()));
    }

    @Test
    void fieldsThisServiceDoesNotKnowAreIgnored() {
        catedra.respondToLogin(FakeCatedraServer.loginWithUnknownFields());

        assertThat(adapter().login()).isEqualTo(
                new TechnicalLoginResult.Success(FakeCatedraServer.TEST_TOKEN, TechnicalIntegrations.complete()));
    }

    @ParameterizedTest
    @CsvSource({"PENDING,PENDING", "FAILED,FAILED", "REVOKED,REVOKED", "SOMETHING_NEW,UNKNOWN"})
    void anotherProvisioningStatusIsStillASuccessThatTheUseCaseWillNotEnable(String sent, ProvisioningStatus expected) {
        catedra.respondToLogin(FakeCatedraServer.loginWithStatus(sent));

        assertThat(adapter().login()).isEqualTo(new TechnicalLoginResult.Success(
                FakeCatedraServer.TEST_TOKEN, TechnicalIntegrations.withStatus(expected)));
    }

    @ParameterizedTest
    @CsvSource({"401,", "403,STUDENT_INTEGRATION_NOT_FOUND", "404,STUDENT_INTEGRATION_NOT_FOUND"})
    void aRejectionIsAConfigurationError(int status, String code) {
        catedra.respondToLogin(code == null ? Response.empty(status) : FakeCatedraServer.problem(status, code));

        TechnicalLoginResult result = adapter().login();

        assertThat(result).isInstanceOfSatisfying(TechnicalLoginResult.ConfigurationError.class, error -> {
            assertThat(error.reason()).contains(String.valueOf(status));
            if (code != null) {
                assertThat(error.reason()).contains(code);
            }
        });
        assertThat(catedra.loginRequests()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    void aServerErrorMeansTheCatedraIsUnavailable(int status) {
        catedra.respondToLogin(Response.empty(status));

        assertThat(adapter().login()).isInstanceOfSatisfying(TechnicalLoginResult.Unavailable.class,
                unavailable -> assertThat(unavailable.reason()).contains(String.valueOf(status)));
    }

    @Test
    void aResponseSlowerThanTheReadTimeoutMeansTheCatedraIsUnavailable() {
        catedra.respondToLogin(FakeCatedraServer.provisionedLogin().after(READ_TIMEOUT.multipliedBy(5)));

        assertThat(adapter().login()).isInstanceOf(TechnicalLoginResult.Unavailable.class);
    }

    @Test
    void aRefusedConnectionMeansTheCatedraIsUnavailable() {
        String baseUrl = catedra.baseUrl();
        catedra.close();

        assertThat(adapterFor(baseUrl).login()).isInstanceOf(TechnicalLoginResult.Unavailable.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"this is not json", ""})
    void aBodyThatCannotBeReadMeansTheCatedraIsUnavailable(String body) {
        catedra.respondToLogin(Response.of(200, body));

        assertThat(adapter().login()).isInstanceOf(TechnicalLoginResult.Unavailable.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id_token", "integration"})
    void aResponseWithoutTheTokenOrTheIntegrationMeansTheCatedraIsUnavailable(String field) {
        catedra.respondToLogin(FakeCatedraServer.loginWithout(field));

        assertThat(adapter().login()).isInstanceOfSatisfying(TechnicalLoginResult.Unavailable.class,
                unavailable -> assertThat(unavailable.reason()).contains(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"redisHost", "redisPort", "redisUsername", "redisPassword",
            "kafkaBootstrapServers", "kafkaConsumerGroupId", "kafkaCatalogTopic"})
    void aResponseWithoutAConnectionValueIsASuccessThatSaysWhatIsMissing(String field) {
        catedra.respondToLogin(FakeCatedraServer.loginWithout(field));

        assertThat(adapter().login()).isInstanceOfSatisfying(TechnicalLoginResult.Success.class,
                success -> assertThat(success.integration().missingData()).containsExactly(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"base-url", "username", "password"})
    void aMissingPropertyIsWarnedAndNoRequestIsSent(String missing, CapturedOutput output) {
        CatedraLoginClientAdapter adapter = new CatedraLoginClientAdapter(new CatedraProperties(
                "base-url".equals(missing) ? "" : catedra.baseUrl(),
                "username".equals(missing) ? null : FakeCatedraServer.TEST_USERNAME,
                "password".equals(missing) ? "  " : FakeCatedraServer.TEST_PASSWORD,
                CONNECT_TIMEOUT, READ_TIMEOUT, LOGIN, SNAPSHOT, REDIS), new TechnicalIntegrationMapper());

        TechnicalLoginResult result = adapter.login();

        assertThat(result).isInstanceOfSatisfying(TechnicalLoginResult.ConfigurationError.class,
                error -> assertThat(error.reason()).contains("catalogo.catedra." + missing));
        assertThat(output).contains("Technical login disabled").contains("catalogo.catedra." + missing);
        assertThat(catedra.receivedRequests()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"http://localhost:8080\"", "'http://localhost:8080'", "localhost:8080", "ftp://catedra.example",
            "http://", "not a url"})
    void anInvalidBaseUrlIsWarnedAndNoLoginIsAttempted(String baseUrl, CapturedOutput output) {
        TechnicalLoginResult result = adapterFor(baseUrl).login();

        assertThat(result).isInstanceOfSatisfying(TechnicalLoginResult.ConfigurationError.class,
                error -> assertThat(error.reason()).contains("catalogo.catedra.base-url"));
        assertThat(output).contains("Technical login disabled").contains("catalogo.catedra.base-url");
    }

    @Test
    void aBaseUrlWithATrailingSlashStillReachesTheLogin() {
        TechnicalLoginResult result = adapterFor(catedra.baseUrl() + "/").login();

        assertThat(result).isInstanceOf(TechnicalLoginResult.Success.class);
        assertThat(catedra.loginRequests()).extracting(ReceivedRequest::path).containsExactly(FakeCatedraServer.LOGIN_PATH);
    }

    @Test
    void anUnexpectedErrorMeansTheCatedraIsUnavailable() {
        TechnicalIntegrationMapper failingMapper = new TechnicalIntegrationMapper() {
            @Override
            public TechnicalIntegration toDomain(IntegrationResponse response) {
                throw new IllegalStateException("unexpected");
            }
        };
        CatedraLoginClientAdapter adapter = new CatedraLoginClientAdapter(properties(catedra.baseUrl()), failingMapper);

        assertThat(adapter.login()).isInstanceOfSatisfying(TechnicalLoginResult.Unavailable.class,
                unavailable -> assertThat(unavailable.reason()).contains("IllegalStateException"));
    }

    @Test
    void noSecretReachesTheOutput(CapturedOutput output) {
        System.out.println(adapter().login());
        catedra.respondToLogin(Response.empty(401));
        System.out.println(adapter().login());
        catedra.respondToLogin(Response.empty(500));
        System.out.println(adapter().login());
        catedra.respondToLogin(FakeCatedraServer.loginWithout("redisHost"));
        System.out.println(adapter().login());
        System.out.println(properties(catedra.baseUrl()));

        assertThat(output)
                .contains("Success[token=***")
                .doesNotContain(FakeCatedraServer.TEST_TOKEN)
                .doesNotContain(FakeCatedraServer.TEST_PASSWORD)
                .doesNotContain(FakeCatedraServer.TEST_REDIS_PASSWORD);
    }

    private CatedraLoginClientAdapter adapter() {
        return adapterFor(catedra.baseUrl());
    }

    private CatedraLoginClientAdapter adapterFor(String baseUrl) {
        return new CatedraLoginClientAdapter(properties(baseUrl), new TechnicalIntegrationMapper());
    }

    private static CatedraProperties properties(String baseUrl) {
        return new CatedraProperties(baseUrl, FakeCatedraServer.TEST_USERNAME, FakeCatedraServer.TEST_PASSWORD,
                CONNECT_TIMEOUT, READ_TIMEOUT, LOGIN, SNAPSHOT, REDIS);
    }
}
