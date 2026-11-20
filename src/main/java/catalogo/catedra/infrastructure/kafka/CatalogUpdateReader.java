package catalogo.catedra.infrastructure.kafka;

import catalogo.catedra.domain.model.CatalogUpdateNotice;
import catalogo.catedra.infrastructure.kafka.dto.CatalogUpdatedMessage;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

// Lee un mensaje CatalogUpdated y decide si es un aviso válido.
@Component
public class CatalogUpdateReader {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String EVENT_TYPE = "CatalogUpdated";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_EVENT_ID_LENGTH = 255;
    private static final int MAX_SOURCE_LENGTH = 64;
    private static final int MAX_LOG_LENGTH = 100;

    public CatalogUpdateReading read(String value) {
        if (value == null || value.isBlank()) {
            return new CatalogUpdateReading.Invalid(null, "empty message");
        }
        CatalogUpdatedMessage message;
        try {
            message = JSON.readValue(value, CatalogUpdatedMessage.class);
        } catch (JacksonException e) {
            return new CatalogUpdateReading.Invalid(null, "unreadable message: " + e.getClass().getSimpleName());
        }
        if (message == null) {
            return new CatalogUpdateReading.Invalid(null, "empty message");
        }
        List<String> problems = new ArrayList<>();
        String eventId = text("eventId", message.getEventId(), MAX_EVENT_ID_LENGTH, problems);
        String eventType = text("eventType", message.getEventType(), Integer.MAX_VALUE, problems);
        if (eventType != null && !EVENT_TYPE.equals(eventType)) {
            problems.add("unexpected eventType " + forLog(eventType));
        }
        Long newVersion = newVersion(message.getNewVersion(), problems);
        Instant occurredAt = instant(message.getOccurredAt(), problems);
        String source = text("source", message.getSource(), MAX_SOURCE_LENGTH, problems);
        schemaVersion(message.getSchemaVersion(), problems);
        if (!problems.isEmpty()) {
            return new CatalogUpdateReading.Invalid(readableEventId(message.getEventId()), String.join(", ", problems));
        }
        return new CatalogUpdateReading.Valid(new CatalogUpdateNotice(eventId, newVersion, occurredAt, source));
    }

    public static String forLog(String value) {
        if (value == null) {
            return null;
        }
        String cut = value.length() > MAX_LOG_LENGTH ? value.substring(0, MAX_LOG_LENGTH) : value;
        StringBuilder clean = new StringBuilder(cut.length());
        cut.chars().forEach(c -> clean.append(Character.isISOControl(c) ? '?' : (char) c));
        return clean.toString();
    }

    private static String text(String name, JsonNode node, int maxLength, List<String> problems) {
        if (absent(node)) {
            problems.add("missing " + name);
            return null;
        }
        if (!node.isString()) {
            problems.add(name + " is not a text");
            return null;
        }
        String value = node.asString();
        if (value.isBlank()) {
            problems.add("empty " + name);
            return null;
        }
        if (value.length() > maxLength) {
            problems.add(name + " longer than " + maxLength + " characters");
            return null;
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            problems.add(name + " with control characters");
            return null;
        }
        return value;
    }

    private static Long newVersion(JsonNode node, List<String> problems) {
        if (absent(node)) {
            problems.add("missing newVersion");
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) {
            problems.add("newVersion is not a non-negative integer");
            return null;
        }
        return node.longValue();
    }

    private static Instant instant(JsonNode node, List<String> problems) {
        if (absent(node)) {
            problems.add("missing occurredAt");
            return null;
        }
        if (!node.isString()) {
            problems.add("occurredAt is not a text");
            return null;
        }
        try {
            return Instant.parse(node.asString());
        } catch (DateTimeParseException e) {
            problems.add("occurredAt is not an instant");
            return null;
        }
    }

    private static void schemaVersion(JsonNode node, List<String> problems) {
        if (absent(node)) {
            problems.add("missing schemaVersion");
        } else if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() != SCHEMA_VERSION) {
            problems.add("unsupported schemaVersion " + forLog(node.toString()));
        }
    }

    private static String readableEventId(JsonNode node) {
        return absent(node) || !node.isString() ? null : forLog(node.asString());
    }

    private static boolean absent(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }
}
