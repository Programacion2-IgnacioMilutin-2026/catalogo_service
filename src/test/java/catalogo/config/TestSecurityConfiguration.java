package catalogo.config;

import catalogo.TestTokens;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistrar;

// La seguridad real con la clave de los tokens de prueba para tests.
@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfig.class, JwtDecoderConfig.class, SecurityProblemHandler.class, ClockConfig.class})
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class TestSecurityConfiguration {

    @Bean
    DynamicPropertyRegistrar testJwtPublicKey() {
        return registry -> registry.add("catalogo.jwt.public-key-location", TestTokens::publicKeyLocation);
    }
}
