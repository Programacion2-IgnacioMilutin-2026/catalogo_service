package catalogo.catedra.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

// Propiedades leidas de la configuracion y declaracion de valores predeterminados.
@ConfigurationProperties("catalogo.catedra")
public record CatedraProperties(
        String baseUrl,
        String username,
        String password,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout,
        @DefaultValue Login login,
        @DefaultValue Snapshot snapshot) {

    public record Login(
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("1s") Duration initialDelay,
            @DefaultValue("2") double multiplier,
            @DefaultValue("30s") Duration maxDelay,
            @DefaultValue("60s") Duration minInterval) {
    }

    public record Snapshot(
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("10s") Duration readTimeout,
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("1s") Duration initialDelay,
            @DefaultValue("2") double multiplier,
            @DefaultValue("30s") Duration maxDelay) {
    }

    @Override
    public String toString() {
        return "CatedraProperties[baseUrl=" + baseUrl + ", username=" + username + ", password=***"
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + ", login=" + login
                + ", snapshot=" + snapshot + "]";
    }
}
