package catalogo.catedra.domain.ports.in;

import catalogo.catedra.domain.model.TechnicalSession;

import java.util.Optional;

// Puerto de entrada para esperar a que termine el login técnico inicial.
public interface AwaitInitialTechnicalSessionUseCase {
    Optional<TechnicalSession> awaitInitialTechnicalSession();
}
