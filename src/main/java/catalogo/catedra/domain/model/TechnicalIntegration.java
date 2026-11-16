package catalogo.catedra.domain.model;

import java.util.ArrayList;
import java.util.List;

// Datos integracion cuenta tecnica.
public record TechnicalIntegration(
        String groupId,
        String redisHost,
        Integer redisPort,
        String redisUsername,
        String redisPassword,
        String kafkaBootstrapServers,
        String kafkaConsumerGroupId,
        String kafkaCatalogTopic,
        ProvisioningStatus provisioningStatus) {

    public boolean isProvisioned() {
        return provisioningStatus == ProvisioningStatus.PROVISIONED;
    }

    // Lo que hace falta para leer Redis y consumir Kafka.
    public List<String> missingData() {
        List<String> missing = new ArrayList<>();
        addIfBlank(missing, "redisHost", redisHost);
        if (redisPort == null) {
            missing.add("redisPort");
        }
        addIfBlank(missing, "redisUsername", redisUsername);
        addIfBlank(missing, "redisPassword", redisPassword);
        addIfBlank(missing, "kafkaBootstrapServers", kafkaBootstrapServers);
        addIfBlank(missing, "kafkaConsumerGroupId", kafkaConsumerGroupId);
        addIfBlank(missing, "kafkaCatalogTopic", kafkaCatalogTopic);
        return List.copyOf(missing);
    }

    private static void addIfBlank(List<String> missing, String name, String value) {
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
    }

    @Override
    public String toString() {
        return "TechnicalIntegration[groupId=" + groupId + ", redisHost=" + redisHost + ", redisPort=" + redisPort
                + ", redisUsername=" + redisUsername + ", redisPassword=" + (redisPassword == null ? "none" : "***")
                + ", kafkaBootstrapServers=" + kafkaBootstrapServers + ", kafkaConsumerGroupId=" + kafkaConsumerGroupId
                + ", kafkaCatalogTopic=" + kafkaCatalogTopic + ", provisioningStatus=" + provisioningStatus + "]";
    }
}
