package catalogo.catedra.infrastructure.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

// Mensaje CatalogUpdated tal como llega del topic.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogUpdatedMessage {

    private JsonNode eventId;
    private JsonNode eventType;
    private JsonNode newVersion;
    private JsonNode occurredAt;
    private JsonNode source;
    private JsonNode schemaVersion;
}
