package catalogo.catedra;

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

// La aplicación completa con la configuración de la cuenta técnica incompleta. Falta la contraseña.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TechnicalSessionWithoutConfigurationIntegrationTest {

    private static final FakeCatedraServer CATEDRA = FakeCatedraServer.start();

    @Autowired
    private TechnicalSessionRepository technicalSessionRepository;

    @Autowired
    private TechnicalSessionService technicalSessionService;

    @DynamicPropertySource
    static void catedraProperties(DynamicPropertyRegistry registry) {
        registry.add("catalogo.catedra.base-url", CATEDRA::baseUrl);
        registry.add("catalogo.catedra.username", () -> FakeCatedraServer.TEST_USERNAME);
    }

    @AfterAll
    static void stopCatedra() {
        CATEDRA.close();
    }

    @Test
    void theServiceStartsWarnsAboutTheMissingPropertyAndSendsNothingToTheCatedra(CapturedOutput output) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> technicalSessionRepository.get().state() == IntegrationState.CONFIGURATION_ERROR);

        assertThat(technicalSessionService.getTechnicalSession()).isEmpty();
        assertThat(technicalSessionService.renewTechnicalSession(null)).isEmpty();
        assertThat(CATEDRA.receivedRequests()).isEmpty();
        assertThat(output)
                .contains("Technical login disabled: missing configuration properties [catalogo.catedra.password]")
                .contains("Technical login configuration error, no retries until restart");
    }
}
