package catalogo.catedra.domain.model;

import java.time.Duration;

// Politicas del pedido del snapshot.
public record CatalogSnapshotPolicy(int maxAttempts, Duration initialDelay, double multiplier, Duration maxDelay) {

    public Duration delayBeforeAttempt(int attempt) {
        if (attempt <= 1) {
            return Duration.ZERO;
        }
        double millis = initialDelay.toMillis() * Math.pow(multiplier, attempt - 2.0);
        return Duration.ofMillis((long) Math.min(millis, maxDelay.toMillis()));
    }
}
