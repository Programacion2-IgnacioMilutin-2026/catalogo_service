package catalogo.catedra.infrastructure.client;

import catalogo.catedra.domain.model.CatalogRedisPolicy;
import catalogo.catedra.domain.model.CatalogSnapshotPolicy;
import catalogo.catedra.domain.model.InitialLoginWait;
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

    @Bean
    CatalogSnapshotPolicy catalogSnapshotPolicy(CatedraProperties catedraProperties) {
        CatedraProperties.Snapshot snapshot = catedraProperties.snapshot();
        return new CatalogSnapshotPolicy(snapshot.maxAttempts(), snapshot.initialDelay(), snapshot.multiplier(),
                snapshot.maxDelay());
    }

    @Bean
    CatalogRedisPolicy catalogRedisPolicy(CatedraProperties catedraProperties) {
        CatedraProperties.Redis redis = catedraProperties.redis();
        return new CatalogRedisPolicy(redis.maxAttempts(), redis.initialDelay(), redis.multiplier(), redis.maxDelay());
    }

    @Bean
    InitialLoginWait initialLoginWait(CatedraProperties catedraProperties) {
        CatedraProperties.InitialLogin initialLogin = catedraProperties.initialLogin();
        return new InitialLoginWait(initialLogin.maxWait(), initialLogin.pollInterval());
    }
}
