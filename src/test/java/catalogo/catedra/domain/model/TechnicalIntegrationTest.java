package catalogo.catedra.domain.model;

import catalogo.catedra.TechnicalIntegrations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalIntegrationTest {

    @Test
    void aCompleteIntegrationHasNothingMissing() {
        assertThat(TechnicalIntegrations.complete().missingData()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"redisHost", "redisPort", "redisUsername", "redisPassword",
            "kafkaBootstrapServers", "kafkaConsumerGroupId", "kafkaCatalogTopic"})
    void eachMissingConnectionValueIsNamed(String field) {
        assertThat(TechnicalIntegrations.without(field).missingData()).containsExactly(field);
    }

    @Test
    void aBlankValueCountsAsMissing() {
        TechnicalIntegration complete = TechnicalIntegrations.complete();
        TechnicalIntegration blankHost = new TechnicalIntegration(complete.groupId(), "  ", complete.redisPort(),
                complete.redisUsername(), complete.redisPassword(), complete.kafkaBootstrapServers(),
                complete.kafkaConsumerGroupId(), complete.kafkaCatalogTopic(), complete.provisioningStatus());

        assertThat(blankHost.missingData()).containsExactly("redisHost");
    }

    @Test
    void severalMissingValuesAreAllNamed() {
        TechnicalIntegration empty = new TechnicalIntegration(null, null, null, null, null, null, null, null,
                ProvisioningStatus.PROVISIONED);

        assertThat(empty.missingData()).containsExactlyElementsOf(TechnicalIntegrations.CONNECTION_DATA);
    }

    @Test
    void theGroupIdIsNotRequiredToConnect() {
        assertThat(TechnicalIntegrations.without("groupId").missingData()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ProvisioningStatus.class)
    void onlyTheProvisionedStatusIsProvisioned(ProvisioningStatus status) {
        assertThat(TechnicalIntegrations.withStatus(status).isProvisioned())
                .isEqualTo(status == ProvisioningStatus.PROVISIONED);
    }

    @Test
    void theRedisPasswordIsNotShownWhenTheIntegrationIsPrinted() {
        TechnicalIntegration integration = TechnicalIntegrations.complete();

        assertThat(integration.toString())
                .doesNotContain(TechnicalIntegrations.REDIS_PASSWORD)
                .contains("proyecto-de-prueba", "PROVISIONED");
    }
}
