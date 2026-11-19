package catalogo.catedra.domain.model;

import java.time.Duration;

// Cuánto se espera a que termine el login tecnico inicial.
public record InitialLoginWait(Duration maxWait, Duration pollInterval) {
}
