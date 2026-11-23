package catalogo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

// Orígenes de navegador que pueden usar la API. Sin ninguno, ningún navegador puede leer las respuestas.
@ConfigurationProperties("catalogo.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {
}
