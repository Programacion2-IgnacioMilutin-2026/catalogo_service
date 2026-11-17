package catalogo.catedra.domain.ports.in;

import catalogo.catedra.domain.model.TechnicalSession;

import java.util.Optional;

public interface GetTechnicalSessionUseCase {

    Optional<TechnicalSession> getTechnicalSession();
}
