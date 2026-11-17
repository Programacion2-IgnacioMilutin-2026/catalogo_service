package catalogo.catedra.domain.ports.in;

import catalogo.catedra.domain.model.TechnicalSession;

import java.util.Optional;

public interface RenewTechnicalSessionUseCase {

    // Recibe la sesión rechazada
    Optional<TechnicalSession> renewTechnicalSession(TechnicalSession rejectedSession);
}
