package catalogo.synchronization.domain.model;

import java.time.Duration;

// Reintentos de una versión cuando falla la base al aplicarla.
public record VerificationPolicy(int persistenceMaxAttempts, Duration initialDelay, double multiplier,
                                 Duration maxDelay) {

    public Duration delayBeforeAttempt(int attempt) {
        if (attempt <= 1) {
            return Duration.ZERO;
        }
        double millis = initialDelay.toMillis() * Math.pow(multiplier, attempt - 2.0);
        return Duration.ofMillis((long) Math.min(millis, maxDelay.toMillis()));
    }
}
