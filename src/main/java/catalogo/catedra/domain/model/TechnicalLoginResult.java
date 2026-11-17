package catalogo.catedra.domain.model;

import java.util.Objects;

// Resultados posibles de un intento de login técnico.
public sealed interface TechnicalLoginResult {

    record Success(String token, TechnicalIntegration integration) implements TechnicalLoginResult {

        public Success {
            Objects.requireNonNull(integration, "integration");
        }

        @Override
        public String toString() {
            return "Success[token=***, integration=" + integration + "]";
        }
    }

    // Credenciales rechazadas, integración inexistente o configuración faltante. Repetir no lo arregla, no se vuelve a intentar
    record ConfigurationError(String reason) implements TechnicalLoginResult {
    }

    // Timeout, conexión rechazada, error del servidor o respuesta incompleta. Puede arreglarse solo, se vuelve a intentar
    record Unavailable(String reason) implements TechnicalLoginResult {
    }
}
