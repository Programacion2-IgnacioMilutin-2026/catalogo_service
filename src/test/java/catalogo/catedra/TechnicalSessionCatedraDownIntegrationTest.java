package catalogo.catedra;

import catalogo.TestcontainersConfiguration;
import catalogo.catedra.application.service.TechnicalSessionService;
import catalogo.catedra.domain.model.IntegrationState;
import catalogo.catedra.domain.ports.out.TechnicalSessionRepository;
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

// La aplicación completa con la cátedra caída.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "catalogo.catedra.login.max-attempts=3",
        "catalogo.catedra.login.initial-delay=500ms"})
@Import(TestcontainersConfiguration.class)
class TechnicalSessionCatedraDownIntegrationTest {

    @Autowired
    private TechnicalSessionRepository technicalSessionRepository;

    @Autowired
    private TechnicalSessionService technicalSessionService;

    @DynamicPropertySource
    static void catedraProperties(DynamicPropertyRegistry registry) {
        registry.add("catalogo.catedra.base-url", TechnicalSessionCatedraDownIntegrationTest::addressWithNobodyListening);
        registry.add("catalogo.catedra.username", () -> FakeCatedraServer.TEST_USERNAME);
        registry.add("catalogo.catedra.password", () -> FakeCatedraServer.TEST_PASSWORD);
    }

    @Test
    void theServiceStartsWithoutWaitingForTheLoginAndEndsDegraded(CapturedOutput output) {
        assertThat(technicalSessionRepository.get().state()).isEqualTo(IntegrationState.CONNECTING);
        assertThat(technicalSessionService.getTechnicalSession()).isEmpty();

        await().atMost(Duration.ofSeconds(15))
                .until(() -> technicalSessionRepository.get().state() == IntegrationState.DEGRADED);

        assertThat(technicalSessionService.getTechnicalSession()).isEmpty();
        assertThat(output)
                .contains("Technical login attempt 1/3 failed")
                .contains("Technical login attempt 3/3 failed")
                .contains("Technical login not completed after 3 attempts: integration degraded")
                .doesNotContain(FakeCatedraServer.TEST_PASSWORD);
    }

    private static String addressWithNobodyListening() {
        FakeCatedraServer stopped = FakeCatedraServer.start();
        String baseUrl = stopped.baseUrl();
        stopped.close();
        return baseUrl;
    }
}
