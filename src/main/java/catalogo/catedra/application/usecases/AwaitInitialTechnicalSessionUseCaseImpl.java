package catalogo.catedra.application.usecases;

import catalogo.catedra.domain.model.InitialLoginWait;
import catalogo.catedra.domain.model.IntegrationState;
import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.in.AwaitInitialTechnicalSessionUseCase;
import catalogo.catedra.domain.ports.out.TechnicalSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

// Espera a que la sesión deje de estar en estado conectando.
@Slf4j
@Component
@RequiredArgsConstructor
public class AwaitInitialTechnicalSessionUseCaseImpl implements AwaitInitialTechnicalSessionUseCase {

    private final TechnicalSessionRepository technicalSessionRepository;
    private final InitialLoginWait initialLoginWait;

    @Override
    public Optional<TechnicalSession> awaitInitialTechnicalSession() {
        TechnicalSession current = technicalSessionRepository.get();
        if (current.state() == IntegrationState.CONNECTING) {
            log.info("Waiting for the initial technical login, at most {}", initialLoginWait.maxWait());
            long deadline = System.nanoTime() + initialLoginWait.maxWait().toNanos();
            while (current.state() == IntegrationState.CONNECTING) {
                if (System.nanoTime() >= deadline) {
                    log.warn("The initial technical login did not finish within {}", initialLoginWait.maxWait());
                    return Optional.empty();
                }
                try {
                    Thread.sleep(initialLoginWait.pollInterval());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
                current = technicalSessionRepository.get();
            }
        }
        return current.isEnabled() ? Optional.of(current) : Optional.empty();
    }
}
