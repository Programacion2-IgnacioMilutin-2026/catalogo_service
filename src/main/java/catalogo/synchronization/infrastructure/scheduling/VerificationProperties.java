package catalogo.synchronization.infrastructure.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

// Propiedades de la verificación periódica y sus valores predeterminados.
@ConfigurationProperties("catalogo.synchronization.verification")
public record VerificationProperties(@DefaultValue PersistenceRetry persistenceRetry) {

    public record PersistenceRetry(
            @DefaultValue("3") int maxAttempts,
            @DefaultValue("1s") Duration initialDelay,
            @DefaultValue("2") double multiplier,
            @DefaultValue("30s") Duration maxDelay) {
    }
}
