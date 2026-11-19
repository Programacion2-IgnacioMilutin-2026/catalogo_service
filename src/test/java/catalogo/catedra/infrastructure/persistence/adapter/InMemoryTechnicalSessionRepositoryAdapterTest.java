package catalogo.catedra.infrastructure.persistence.adapter;

import catalogo.catedra.TechnicalIntegrations;
import catalogo.catedra.domain.model.TechnicalSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTechnicalSessionRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    private final InMemoryTechnicalSessionRepositoryAdapter repository = new InMemoryTechnicalSessionRepositoryAdapter();

    @Test
    void startsWithASessionThatIsConnecting() {
        assertThat(repository.get()).isEqualTo(TechnicalSession.connecting());
    }

    @Test
    void aSavedSessionReplacesThePreviousOne() {
        TechnicalSession enabled = TechnicalSession.enabled("test-technical-jwt", TechnicalIntegrations.complete(), NOW);

        repository.save(TechnicalSession.degraded(NOW));
        repository.save(enabled);

        assertThat(repository.get()).isEqualTo(enabled);
    }
}
