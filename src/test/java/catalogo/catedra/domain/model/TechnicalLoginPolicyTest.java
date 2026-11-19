package catalogo.catedra.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalLoginPolicyTest {

    private final TechnicalLoginPolicy policy = new TechnicalLoginPolicy(
            5, Duration.ofSeconds(1), 2, Duration.ofSeconds(30), Duration.ofSeconds(60));

    @Test
    void theFirstAttemptDoesNotWait() {
        assertThat(policy.delayBeforeAttempt(1)).isEqualTo(Duration.ZERO);
    }

    @Test
    void theWaitDoublesBeforeEachNewAttempt() {
        assertThat(policy.delayBeforeAttempt(2)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayBeforeAttempt(3)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayBeforeAttempt(4)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.delayBeforeAttempt(5)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void theWaitNeverExceedsTheMaximum() {
        assertThat(policy.delayBeforeAttempt(7)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.delayBeforeAttempt(50)).isEqualTo(Duration.ofSeconds(30));
    }
}
