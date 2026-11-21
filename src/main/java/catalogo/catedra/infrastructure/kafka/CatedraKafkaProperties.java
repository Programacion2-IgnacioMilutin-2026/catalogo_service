package catalogo.catedra.infrastructure.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

// Cómo consume el servicio el topic del catálogo.
@ConfigurationProperties("catalogo.catedra.kafka")
public record CatedraKafkaProperties(
        @DefaultValue("true") Boolean enabled,
        @DefaultValue("10m") Duration maxPollInterval,
        String consumerGroup) {

    public CatedraKafkaProperties {
        enabled = enabled == null || enabled;
        maxPollInterval = maxPollInterval == null ? Duration.ofMinutes(10) : maxPollInterval;
        consumerGroup = consumerGroup == null || consumerGroup.isBlank() ? null : consumerGroup;
    }
}
