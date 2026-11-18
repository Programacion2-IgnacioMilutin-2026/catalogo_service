package catalogo.catedra.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// Esperas antes de cada intento del pedido del snapshot.
class CatalogSnapshotPolicyTest {

    private final CatalogSnapshotPolicy policy = new CatalogSnapshotPolicy(
            5, Duration.ofSeconds(1), 2, Duration.ofSeconds(30));

    @Test
    void theFirstAttemptDoesNotWait() {
        assertThat(policy.delayBeforeAttempt(1)).isZero();
    }

    @Test
    void theWaitGrowsBeforeEachRetry() {
        assertThat(policy.delayBeforeAttempt(2)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayBeforeAttempt(3)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayBeforeAttempt(4)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.delayBeforeAttempt(5)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void theWaitNeverGoesOverTheCap() {
        assertThat(policy.delayBeforeAttempt(10)).isEqualTo(Duration.ofSeconds(30));
    }
}
