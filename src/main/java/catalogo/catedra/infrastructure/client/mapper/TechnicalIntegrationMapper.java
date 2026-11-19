package catalogo.catedra.infrastructure.client.mapper;

import catalogo.catedra.domain.model.ProvisioningStatus;
import catalogo.catedra.domain.model.TechnicalIntegration;
import catalogo.catedra.infrastructure.client.dto.IntegrationResponse;
import org.springframework.stereotype.Component;

import java.util.Arrays;

// Convertir la respuesta de la integracion al modelo de dominio.
@Component
public class TechnicalIntegrationMapper {

    public TechnicalIntegration toDomain(IntegrationResponse response) {
        if (response == null) {
            return null;
        }
        return new TechnicalIntegration(
                response.getGroupId(),
                response.getRedisHost(),
                response.getRedisPort(),
                response.getRedisUsername(),
                response.getRedisPassword(),
                response.getKafkaBootstrapServers(),
                response.getKafkaConsumerGroupId(),
                response.getKafkaCatalogTopic(),
                provisioningStatusOf(response.getProvisioningStatus()));
    }

    // Un estado que la cátedra agregue más adelante no rompe la lectura: queda como UNKNOWN, que no habilita la integración
    private ProvisioningStatus provisioningStatusOf(String value) {
        return Arrays.stream(ProvisioningStatus.values())
                .filter(status -> status != ProvisioningStatus.UNKNOWN && status.name().equals(value))
                .findFirst()
                .orElse(ProvisioningStatus.UNKNOWN);
    }
}
