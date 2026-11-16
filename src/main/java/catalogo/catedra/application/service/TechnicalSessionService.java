package catalogo.catedra.application.service;

import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.in.GetTechnicalSessionUseCase;
import catalogo.catedra.domain.ports.in.RenewTechnicalSessionUseCase;
import catalogo.catedra.domain.ports.in.StartTechnicalSessionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

// Servicio que brinda la implementacion de los casos de uso relacionados con el inicio de sesion de la cuenta tecnica.
@Service
@RequiredArgsConstructor
public class TechnicalSessionService {

    private final StartTechnicalSessionUseCase startTechnicalSessionUseCase;
    private final GetTechnicalSessionUseCase getTechnicalSessionUseCase;
    private final RenewTechnicalSessionUseCase renewTechnicalSessionUseCase;

    public TechnicalSession startTechnicalSession() {
        return startTechnicalSessionUseCase.startTechnicalSession();
    }

    public Optional<TechnicalSession> getTechnicalSession() {
        return getTechnicalSessionUseCase.getTechnicalSession();
    }

    public Optional<TechnicalSession> renewTechnicalSession(TechnicalSession rejectedSession) {
        return renewTechnicalSessionUseCase.renewTechnicalSession(rejectedSession);
    }
}
