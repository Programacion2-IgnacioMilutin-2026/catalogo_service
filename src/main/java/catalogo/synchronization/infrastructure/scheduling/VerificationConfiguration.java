package catalogo.synchronization.infrastructure.scheduling;

import catalogo.synchronization.domain.model.VerificationPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Convierte las propiedades de la verificación en la política del dominio.
@Configuration(proxyBeanMethods = false)
public class VerificationConfiguration {

    @Bean
    VerificationPolicy verificationPolicy(VerificationProperties verificationProperties) {
        VerificationProperties.PersistenceRetry retry = verificationProperties.persistenceRetry();
        return new VerificationPolicy(retry.maxAttempts(), retry.initialDelay(), retry.multiplier(), retry.maxDelay());
    }
}
