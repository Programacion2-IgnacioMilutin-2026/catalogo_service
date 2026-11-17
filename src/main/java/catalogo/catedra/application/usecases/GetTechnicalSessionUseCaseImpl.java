package catalogo.catedra.application.usecases;

import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.in.GetTechnicalSessionUseCase;
import catalogo.catedra.domain.ports.out.TechnicalSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

// Devuelve la sesión vigente solo si está habilitada.
@Component
@RequiredArgsConstructor
public class GetTechnicalSessionUseCaseImpl implements GetTechnicalSessionUseCase {

    private final TechnicalSessionRepository technicalSessionRepository;

    @Override
    public Optional<TechnicalSession> getTechnicalSession() {
        TechnicalSession current = technicalSessionRepository.get();
        return current.isEnabled() ? Optional.of(current) : Optional.empty();
    }
}
