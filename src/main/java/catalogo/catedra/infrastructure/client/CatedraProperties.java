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
        @DefaultValue("10s") Duration readTimeout) {

    @Override
    public String toString() {
        return "CatedraProperties[baseUrl=" + baseUrl + ", username=" + username + ", password=***"
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
    }
}
