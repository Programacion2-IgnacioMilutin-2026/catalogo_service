package catalogo.catedra.domain.model;

import catalogo.catedra.TechnicalIntegrations;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalSessionTest {

    private static final String TOKEN = "test-technical-jwt";
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(60);
    private static final Instant LAST_ATTEMPT = Instant.parse("2026-11-15T12:00:00Z");

    @Test
    void aSessionWithAConfigurationErrorNeverAllowsAnotherLogin() {
        TechnicalSession session = TechnicalSession.configurationError(LAST_ATTEMPT);

        assertThat(session.canAttemptLogin(LAST_ATTEMPT.plus(Duration.ofDays(30)), MIN_INTERVAL)).isFalse();
    }

    @Test
    void whileTheInitialLoginIsRunningNoOtherLoginIsAllowed() {
        TechnicalSession session = TechnicalSession.connecting();

        assertThat(session.canAttemptLogin(LAST_ATTEMPT.plus(Duration.ofDays(30)), MIN_INTERVAL)).isFalse();
    }

    @Test
    void aDegradedSessionAllowsALoginOnlyAfterTheMinimumInterval() {
        TechnicalSession session = TechnicalSession.degraded(LAST_ATTEMPT);

        assertThat(session.canAttemptLogin(LAST_ATTEMPT.plusSeconds(59), MIN_INTERVAL)).isFalse();
        assertThat(session.canAttemptLogin(LAST_ATTEMPT.plusSeconds(60), MIN_INTERVAL)).isTrue();
        assertThat(session.canAttemptLogin(LAST_ATTEMPT.plusSeconds(61), MIN_INTERVAL)).isTrue();
    }

    @Test
    void anEnabledSessionAllowsALoginOnlyAfterTheMinimumInterval() {
        TechnicalSession session = TechnicalSession.enabled(TOKEN, TechnicalIntegrations.complete(), LAST_ATTEMPT);

        assertThat(session.canAttemptLogin(LAST_ATTEMPT.plusSeconds(59), MIN_INTERVAL)).isFalse();
        assertThat(session.canAttemptLogin(LAST_ATTEMPT.plusSeconds(60), MIN_INTERVAL)).isTrue();
    }

    @Test
    void onlyAnEnabledSessionIsEnabled() {
        assertThat(TechnicalSession.enabled(TOKEN, TechnicalIntegrations.complete(), LAST_ATTEMPT).isEnabled()).isTrue();
        assertThat(TechnicalSession.connecting().isEnabled()).isFalse();
        assertThat(TechnicalSession.degraded(LAST_ATTEMPT).isEnabled()).isFalse();
        assertThat(TechnicalSession.configurationError(LAST_ATTEMPT).isEnabled()).isFalse();
    }

    @Test
    void aSessionThatIsNotEnabledKeepsNoTokenNorIntegration() {
        assertThat(TechnicalSession.degraded(LAST_ATTEMPT))
                .extracting(TechnicalSession::token, TechnicalSession::integration)
                .containsOnlyNulls();
        assertThat(TechnicalSession.configurationError(LAST_ATTEMPT))
                .extracting(TechnicalSession::token, TechnicalSession::integration)
                .containsOnlyNulls();
    }

    @Test
    void neitherTheTokenNorTheRedisPasswordIsShownWhenTheSessionIsPrinted() {
        TechnicalSession session = TechnicalSession.enabled(TOKEN, TechnicalIntegrations.complete(), LAST_ATTEMPT);

        assertThat(session.toString())
                .doesNotContain(TOKEN)
                .doesNotContain(TechnicalIntegrations.REDIS_PASSWORD)
                .contains("ENABLED");
    }

    @Test
    void neitherTheTokenNorTheRedisPasswordIsShownWhenASuccessfulLoginIsPrinted() {
        TechnicalLoginResult result = new TechnicalLoginResult.Success(TOKEN, TechnicalIntegrations.complete());

        assertThat(result.toString())
                .doesNotContain(TOKEN)
                .doesNotContain(TechnicalIntegrations.REDIS_PASSWORD);
    }
}
