package catalogo.synchronization;

import catalogo.TestcontainersConfiguration;
import catalogo.synchronization.infrastructure.scheduling.CatalogVerificationScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

// En los tests la verificación periódica está apagada.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VerificationSchedulerDisabledInTestsTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSchedulerIsNotCreatedInTheTests() {
        assertThat(context.getBeansOfType(CatalogVerificationScheduler.class)).isEmpty();
    }
}
