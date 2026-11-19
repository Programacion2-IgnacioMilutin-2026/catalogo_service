package catalogo.catedra.application.usecases;

import catalogo.catedra.TechnicalIntegrations;
import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.out.TechnicalSessionRepository;
import catalogo.catedra.infrastructure.persistence.adapter.InMemoryTechnicalSessionRepositoryAdapter;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GetTechnicalSessionUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    private final TechnicalSessionRepository repository = new InMemoryTechnicalSessionRepositoryAdapter();
    private final GetTechnicalSessionUseCaseImpl useCase = new GetTechnicalSessionUseCaseImpl(repository);

    @Test
    void anEnabledSessionIsReturnedAsItIs() {
        TechnicalSession enabled = TechnicalSession.enabled("token-a", TechnicalIntegrations.complete(), NOW);
        repository.save(enabled);

        assertThat(useCase.getTechnicalSession()).contains(enabled);
    }

    @Test
    void theCurrentSessionIsReadOnEachCall() {
        repository.save(TechnicalSession.enabled("token-a", TechnicalIntegrations.complete(), NOW));
        useCase.getTechnicalSession();
        TechnicalSession renewed = TechnicalSession.enabled("token-b", TechnicalIntegrations.complete(), NOW.plusSeconds(90));
        repository.save(renewed);

        assertThat(useCase.getTechnicalSession()).contains(renewed);
    }

    @Test
    void aSessionThatIsNotEnabledIsNotReturned() {
        assertThat(useCase.getTechnicalSession()).isEmpty();

        repository.save(TechnicalSession.degraded(NOW));
        assertThat(useCase.getTechnicalSession()).isEmpty();

        repository.save(TechnicalSession.configurationError(NOW));
        assertThat(useCase.getTechnicalSession()).isEmpty();
    }
}
