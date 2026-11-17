package catalogo.catedra;

import catalogo.TestcontainersConfiguration;
import catalogo.catedra.application.service.TechnicalSessionService;
import catalogo.catedra.domain.model.TechnicalSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// La aplicación completa contra una cátedra simulada que acepta el login técnico.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TechnicalSessionIntegrationTest {

    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();

    @Autowired
    private TechnicalSessionService technicalSessionService;

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

    @Test
    void atStartupTheServiceLogsInOnceAndTheSessionIsEnabledWithoutShowingAnySecret(CapturedOutput output) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> technicalSessionService.getTechnicalSession().isPresent());

        Optional<TechnicalSession> session = technicalSessionService.getTechnicalSession();
        assertThat(session).map(TechnicalSession::token).contains(FakeCatedraServer.TEST_TOKEN);
        assertThat(session).map(TechnicalSession::integration).contains(TechnicalIntegrations.complete());

        assertThat(CATEDRA.receivedRequests()).hasSize(1);
        assertThat(CATEDRA.loginRequests()).singleElement().satisfies(request -> {
            JsonNode body = JsonMapper.builder().build().readTree(request.body());
            assertThat(body.get("username").asString()).isEqualTo(FakeCatedraServer.TEST_USERNAME);
            assertThat(body.get("password").asString()).isEqualTo(FakeCatedraServer.TEST_PASSWORD);
            assertThat(body.get("rememberMe").asBoolean()).isTrue();
        });

        System.out.println(session);
        assertThat(output)
                .contains("Technical login succeeded: groupId=proyecto-de-prueba, provisioningStatus=PROVISIONED")
                .doesNotContain(FakeCatedraServer.TEST_TOKEN)
                .doesNotContain(FakeCatedraServer.TEST_PASSWORD)
                .doesNotContain(FakeCatedraServer.TEST_REDIS_PASSWORD);
    }
}
