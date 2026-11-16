package catalogo.catedra.infrastructure.client;

import catalogo.catedra.domain.model.TechnicalLoginPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Convierte las propiedades de reintento en la política del dominio, para que los casos de uso no conozcan la configuración.
@Configuration(proxyBeanMethods = false)
public class CatedraClientConfiguration {

    @Bean
    TechnicalLoginPolicy technicalLoginPolicy(CatedraProperties catedraProperties) {
        CatedraProperties.Login login = catedraProperties.login();
        return new TechnicalLoginPolicy(login.maxAttempts(), login.initialDelay(), login.multiplier(),
                login.maxDelay(), login.minInterval());
    }
}
