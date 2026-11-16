package catalogo.catedra;

import catalogo.FakeHttpServer.Response;
import catalogo.TestcontainersConfiguration;
import catalogo.catedra.application.service.TechnicalSessionService;
import catalogo.catedra.domain.model.IntegrationState;
import catalogo.catedra.domain.ports.out.TechnicalSessionRepository;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// La aplicación completa contra una cátedra que rechaza las credenciales de la cuenta técnica.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TechnicalSessionRejectedIntegrationTest {

    private static final FakeCatedraServer CATEDRA = rejectingCatedra();

    @Autowired
    private TechnicalSessionRepository technicalSessionRepository;

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
    void theServiceStartsAndTheSessionEndsWithAConfigurationErrorAfterASingleLogin(CapturedOutput output) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> technicalSessionRepository.get().state() == IntegrationState.CONFIGURATION_ERROR);

        assertThat(technicalSessionService.getTechnicalSession()).isEmpty();
        assertThat(technicalSessionService.renewTechnicalSession(null)).isEmpty();
        assertThat(CATEDRA.loginRequests()).hasSize(1);
        assertThat(output)
                .contains("Technical login configuration error, no retries until restart")
                .contains("HTTP 401")
                .doesNotContain(FakeCatedraServer.TEST_PASSWORD);
    }

    private static FakeCatedraServer rejectingCatedra() {
        FakeCatedraServer catedra = FakeCatedraServer.start();
        catedra.respondToLogin(Response.empty(401));
        return catedra;
    }
}
