package catalogo.catedra.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Respuesta de datos de la integración.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntegrationResponse {

    private String groupId;
    private String redisHost;
    private Integer redisPort;
    private String redisUsername;
    private String redisPassword;
    private String kafkaBootstrapServers;
    private String kafkaConsumerGroupId;
    private String kafkaCatalogTopic;
    private String provisioningStatus;
}
