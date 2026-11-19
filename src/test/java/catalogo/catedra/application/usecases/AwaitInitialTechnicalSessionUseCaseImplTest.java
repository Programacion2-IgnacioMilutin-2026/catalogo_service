package catalogo.catedra.application.usecases;

import catalogo.catedra.TechnicalIntegrations;
import catalogo.catedra.domain.model.InitialLoginWait;
import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.infrastructure.persistence.adapter.InMemoryTechnicalSessionRepositoryAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// La espera a que temrine el login técnico inicial.
@ExtendWith(OutputCaptureExtension.class)
class AwaitInitialTechnicalSessionUseCaseImplTest {

    private static final Instant AT = Instant.parse("2026-11-18T12:00:00Z");
    private static final TechnicalSession ENABLED =
            TechnicalSession.enabled("test-technical-jwt", TechnicalIntegrations.complete(), AT);

    private final InMemoryTechnicalSessionRepositoryAdapter repository = new InMemoryTechnicalSessionRepositoryAdapter();

    @Test
    void anAlreadyEnabledSessionIsReturnedWithoutWaiting() {
        repository.save(ENABLED);

        long start = System.nanoTime();
        Optional<TechnicalSession> session = useCase(Duration.ofSeconds(5)).awaitInitialTechnicalSession();

        assertThat(session).contains(ENABLED);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(100));
    }

    @Test
    void itWaitsForTheInitialLoginAndContinuesWithItsSession(CapturedOutput output) {
        finishLoginAfter(Duration.ofMillis(150), ENABLED);

        long start = System.nanoTime();
        Optional<TechnicalSession> session = useCase(Duration.ofSeconds(5)).awaitInitialTechnicalSession();

        assertThat(session).contains(ENABLED);
        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isGreaterThanOrEqualTo(Duration.ofMillis(150))
                .isLessThan(Duration.ofSeconds(2));
        assertThat(output).contains("Waiting for the initial technical login").doesNotContain("test-technical-jwt");
    }

    @Test
    void aLoginThatEndsDegradedGivesNoSession() {
        finishLoginAfter(Duration.ofMillis(100), TechnicalSession.degraded(AT));

        assertThat(useCase(Duration.ofSeconds(5)).awaitInitialTechnicalSession()).isEmpty();
    }

    @Test
    void aConfigurationErrorGivesNoSessionWithoutWaiting() {
        repository.save(TechnicalSession.configurationError(AT));

        assertThat(useCase(Duration.ofSeconds(5)).awaitInitialTechnicalSession()).isEmpty();
    }

    @Test
    void aLoginThatDoesNotFinishBeforeTheLimitGivesNoSession(CapturedOutput output) {
        long start = System.nanoTime();
        Optional<TechnicalSession> session = useCase(Duration.ofMillis(200)).awaitInitialTechnicalSession();

        assertThat(session).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(200));
        assertThat(output).contains("The initial technical login did not finish within");
    }

    private AwaitInitialTechnicalSessionUseCaseImpl useCase(Duration maxWait) {
        return new AwaitInitialTechnicalSessionUseCaseImpl(repository, new InitialLoginWait(maxWait, Duration.ofMillis(20)));
    }

    private void finishLoginAfter(Duration delay, TechnicalSession result) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            repository.save(result);
        });
    }
}
