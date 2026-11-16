package catalogo.catedra.infrastructure.persistence.adapter;

import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.out.TechnicalSessionRepository;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

// Guarda la sesión técnica solo en memoria.
@Component
public class InMemoryTechnicalSessionRepositoryAdapter implements TechnicalSessionRepository {

    private final AtomicReference<TechnicalSession> current = new AtomicReference<>(TechnicalSession.connecting());

    @Override
    public TechnicalSession get() {
        return current.get();
    }

    @Override
    public void save(TechnicalSession technicalSession) {
        current.set(technicalSession);
    }
}
