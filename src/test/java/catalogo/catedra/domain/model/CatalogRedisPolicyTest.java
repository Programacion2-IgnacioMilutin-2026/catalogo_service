package catalogo.catedra.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// Las esperas antes de cada intento de lectura de Redis.
class CatalogRedisPolicyTest {

    @Test
    void theDefaultValuesWaitOneAndTwoSeconds() {
        CatalogRedisPolicy policy = new CatalogRedisPolicy(3, Duration.ofSeconds(1), 2, Duration.ofSeconds(30));

        assertThat(policy.delayBeforeAttempt(1)).isZero();
        assertThat(policy.delayBeforeAttempt(2)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayBeforeAttempt(3)).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void theWaitNeverExceedsTheMaximum() {
        CatalogRedisPolicy policy = new CatalogRedisPolicy(10, Duration.ofSeconds(1), 2, Duration.ofSeconds(5));

        assertThat(policy.delayBeforeAttempt(8)).isEqualTo(Duration.ofSeconds(5));
    }
}
