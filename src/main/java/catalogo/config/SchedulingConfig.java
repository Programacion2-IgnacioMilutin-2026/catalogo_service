package catalogo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Habilita las tareas programadas del servicio.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
