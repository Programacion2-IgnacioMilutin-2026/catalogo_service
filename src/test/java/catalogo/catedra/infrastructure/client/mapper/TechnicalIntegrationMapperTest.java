package catalogo.catedra.infrastructure.client.mapper;

import catalogo.catedra.domain.model.ProvisioningStatus;
import catalogo.catedra.domain.model.TechnicalIntegration;
import catalogo.catedra.infrastructure.client.dto.IntegrationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalIntegrationMapperTest {

    private final TechnicalIntegrationMapper mapper = new TechnicalIntegrationMapper();

    @Test
    void everyValueOfTheResponseReachesTheDomain() {
        IntegrationResponse response = fullResponse("PROVISIONED");
        assertThat(response).hasNoNullFieldsOrProperties();

        TechnicalIntegration integration = mapper.toDomain(response);

        assertThat(integration).isEqualTo(new TechnicalIntegration(
                "group", "redis-host", 6380, "redis-user", "redis-pass",
                "kafka-servers", "kafka-group", "kafka-topic", ProvisioningStatus.PROVISIONED));
        assertThat(integration).hasNoNullFieldsOrProperties();
    }

    @Test
    void aNullResponseMapsToNull() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({"PENDING,PENDING", "PROVISIONED,PROVISIONED", "FAILED,FAILED", "REVOKED,REVOKED",
            "SOMETHING_NEW,UNKNOWN", "UNKNOWN,UNKNOWN", "provisioned,UNKNOWN", ",UNKNOWN"})
    void aStatusThisServiceDoesNotKnowBecomesUnknown(String sent, ProvisioningStatus expected) {
        assertThat(mapper.toDomain(fullResponse(sent)).provisioningStatus()).isEqualTo(expected);
    }

    // Ejemplo para los tests.
    private static IntegrationResponse fullResponse(String provisioningStatus) {
        return IntegrationResponse.builder()
                .groupId("group")
                .redisHost("redis-host")
                .redisPort(6380)
                .redisUsername("redis-user")
                .redisPassword("redis-pass")
                .kafkaBootstrapServers("kafka-servers")
                .kafkaConsumerGroupId("kafka-group")
                .kafkaCatalogTopic("kafka-topic")
                .provisioningStatus(provisioningStatus)
                .build();
    }
}
