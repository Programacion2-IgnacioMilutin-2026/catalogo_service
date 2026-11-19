package catalogo.catedra;

import catalogo.catedra.domain.model.ProvisioningStatus;
import catalogo.catedra.domain.model.TechnicalIntegration;

import java.util.List;

// Integraciones de ejemplo para los tests con valores ficticios.
public final class TechnicalIntegrations {

    public static final String REDIS_PASSWORD = "test-redis-password";

    // Datos obligatorios integración para sincronizar.
    public static final List<String> CONNECTION_DATA = List.of(
            "redisHost", "redisPort", "redisUsername", "redisPassword",
            "kafkaBootstrapServers", "kafkaConsumerGroupId", "kafkaCatalogTopic");

    private TechnicalIntegrations() {
    }

    public static TechnicalIntegration complete() {
        return withStatus(ProvisioningStatus.PROVISIONED);
    }

    public static TechnicalIntegration withStatus(ProvisioningStatus status) {
        return new TechnicalIntegration(
                "proyecto-de-prueba",
                "redis.catedra.internal",
                6379,
                "grp_proyecto-de-prueba",
                REDIS_PASSWORD,
                "kafka.catedra.internal:9092",
                "alumnos-proyecto-de-prueba",
                "catedra.catalog.proyecto-de-prueba",
                status);
    }

    // Una integración a la que le falta un solo dato
    public static TechnicalIntegration without(String field) {
        TechnicalIntegration complete = complete();
        return new TechnicalIntegration(
                "groupId".equals(field) ? null : complete.groupId(),
                "redisHost".equals(field) ? null : complete.redisHost(),
                "redisPort".equals(field) ? null : complete.redisPort(),
                "redisUsername".equals(field) ? null : complete.redisUsername(),
                "redisPassword".equals(field) ? null : complete.redisPassword(),
                "kafkaBootstrapServers".equals(field) ? null : complete.kafkaBootstrapServers(),
                "kafkaConsumerGroupId".equals(field) ? null : complete.kafkaConsumerGroupId(),
                "kafkaCatalogTopic".equals(field) ? null : complete.kafkaCatalogTopic(),
                complete.provisioningStatus());
    }
}
