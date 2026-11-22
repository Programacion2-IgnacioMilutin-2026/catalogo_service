package catalogo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Ubicación del archivo PEM con la clave pública de turnos que valida los tokens de los usuarios.
@ConfigurationProperties("catalogo.jwt")
public record JwtProperties(String publicKeyLocation) {
}
