package catalogo.catedra.infrastructure.kafka;

import catalogo.catedra.domain.model.CatalogUpdateNotice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// La lectura de un mensaje CatalogUpdated.
class CatalogUpdateReaderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String EVENT_ID = "81545656-d410-4dd1-8a06-18142a406205";
    private static final String REAL = "{\"eventId\":\"81545656-d410-4dd1-8a06-18142a406205\","
            + "\"eventType\":\"CatalogUpdated\",\"newVersion\":368,"
            + "\"occurredAt\":\"2026-11-19T15:18:49.018998134Z\",\"source\":\"SCHEDULED\",\"schemaVersion\":1}";
    private static final Object ABSENT = new Object();

    private final CatalogUpdateReader reader = new CatalogUpdateReader();

    @Test
    void aRealMessageIsReadWithItsFields() {
        assertThat(reader.read(REAL)).isEqualTo(new CatalogUpdateReading.Valid(new CatalogUpdateNotice(EVENT_ID, 368,
                Instant.parse("2026-11-19T15:18:49.018998134Z"), "SCHEDULED")));
    }

    static Stream<Arguments> acceptedVariants() {
        return Stream.of(
                arguments("occurredAt without fraction", message("occurredAt", "2026-11-19T15:18:49Z")),
                arguments("unknown fields", message("priority", "high")),
                arguments("a source that is not an example", message("source", "BULK_IMPORT")),
                arguments("eventId of 255 characters", message("eventId", "e".repeat(255))),
                arguments("source of 64 characters", message("source", "S".repeat(64))),
                arguments("newVersion zero", message("newVersion", 0)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedVariants")
    void validVariantsAreAccepted(String variant, String json) {
        assertThat(reader.read(json)).isInstanceOf(CatalogUpdateReading.Valid.class);
    }

    static Stream<Arguments> defects() {
        return Stream.of(
                arguments("null value", null, "empty message"),
                arguments("blank value", "  ", "empty message"),
                arguments("JSON null", "null", "empty message"),
                arguments("unreadable JSON", "{\"eventId\":", "unreadable message"),
                arguments("an array", "[1, 2]", "unreadable message"),
                arguments("a text", "\"CatalogUpdated\"", "unreadable message"),
                arguments("missing eventId", message("eventId", ABSENT), "missing eventId"),
                arguments("null eventId", message("eventId", null), "missing eventId"),
                arguments("empty eventId", message("eventId", " "), "empty eventId"),
                arguments("numeric eventId", message("eventId", 7), "eventId is not a text"),
                arguments("eventId of 256 characters", message("eventId", "e".repeat(256)),
                        "eventId longer than 255 characters"),
                arguments("eventId with a line break", message("eventId", "abc\ndef"),
                        "eventId with control characters"),
                arguments("eventId with the null character", message("eventId", "abc\u0000def"),
                        "eventId with control characters"),
                arguments("missing eventType", message("eventType", ABSENT), "missing eventType"),
                arguments("another eventType", message("eventType", "CatalogDeleted"),
                        "unexpected eventType CatalogDeleted"),
                arguments("missing newVersion", message("newVersion", ABSENT), "missing newVersion"),
                arguments("negative newVersion", message("newVersion", -1),
                        "newVersion is not a non-negative integer"),
                arguments("decimal newVersion", message("newVersion", 1.5),
                        "newVersion is not a non-negative integer"),
                arguments("newVersion as text", message("newVersion", "368"),
                        "newVersion is not a non-negative integer"),
                arguments("missing occurredAt", message("occurredAt", ABSENT), "missing occurredAt"),
                arguments("occurredAt that is not an instant", message("occurredAt", "yesterday"),
                        "occurredAt is not an instant"),
                arguments("occurredAt without a zone", message("occurredAt", "2026-11-19T15:18:49"),
                        "occurredAt is not an instant"),
                arguments("missing source", message("source", ABSENT), "missing source"),
                arguments("empty source", message("source", ""), "empty source"),
                arguments("source of 65 characters", message("source", "S".repeat(65)),
                        "source longer than 64 characters"),
                arguments("source with a control character", message("source", "SCHED\u0001ULED"),
                        "source with control characters"),
                arguments("missing schemaVersion", message("schemaVersion", ABSENT), "missing schemaVersion"),
                arguments("schemaVersion 2", message("schemaVersion", 2), "unsupported schemaVersion 2"),
                arguments("schemaVersion as text", message("schemaVersion", "1"), "unsupported schemaVersion"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("defects")
    void defectiveMessagesAreInvalidWithTheirReason(String defect, String json, String reason) {
        CatalogUpdateReading reading = reader.read(json);

        assertThat(reading).isInstanceOf(CatalogUpdateReading.Invalid.class);
        assertThat(((CatalogUpdateReading.Invalid) reading).reason()).contains(reason);
    }

    @Test
    void anInvalidMessageKeepsItsEventIdForTheLog() {
        CatalogUpdateReading reading = reader.read(message("schemaVersion", 2));

        assertThat(((CatalogUpdateReading.Invalid) reading).eventId()).isEqualTo(EVENT_ID);
    }

    @Test
    void anInvalidEventIdReachesTheLogCutAndWithoutControlCharacters() {
        CatalogUpdateReading.Invalid withBreak = (CatalogUpdateReading.Invalid) reader.read(
                message("eventId", "fake\n2026-11-19 ERROR forged line"));
        CatalogUpdateReading.Invalid tooLong = (CatalogUpdateReading.Invalid) reader.read(
                message("eventId", "e".repeat(300)));

        assertThat(withBreak.eventId()).isEqualTo("fake?2026-11-19 ERROR forged line");
        assertThat(tooLong.eventId()).hasSize(100);
    }

    @Test
    void aLongWrongEventTypeReachesTheReasonCut() {
        CatalogUpdateReading.Invalid reading = (CatalogUpdateReading.Invalid) reader.read(
                message("eventType", "CatalogDeleted" + "x".repeat(200)));

        assertThat(reading.reason()).startsWith("unexpected eventType CatalogDeleted");
        assertThat(reading.reason()).hasSize("unexpected eventType ".length() + 100);
    }

    @Test
    void aValueWithControlCharactersNeverReachesTheReason() {
        CatalogUpdateReading.Invalid reading = (CatalogUpdateReading.Invalid) reader.read(
                message("eventType", "Catalog\r\n2026-11-19 ERROR forged line"));

        assertThat(reading.reason()).isEqualTo("eventType with control characters");
    }

    @Test
    void forLogCutsToOneHundredCharactersAndReplacesControlCharacters() {
        assertThat(CatalogUpdateReader.forLog("a".repeat(150))).hasSize(100);
        assertThat(CatalogUpdateReader.forLog("a\nb\u0000c\td\u007Fe")).isEqualTo("a?b?c?d?e");
        assertThat(CatalogUpdateReader.forLog("SCHEDULED")).isEqualTo("SCHEDULED");
        assertThat(CatalogUpdateReader.forLog(null)).isNull();
    }

    // El mensaje real con un campo cambiado; ABSENT lo quita.
    private static String message(String field, Object value) {
        Map<String, Object> fields = new LinkedHashMap<>(JSON.readValue(REAL, Map.class));
        if (value == ABSENT) {
            fields.remove(field);
        } else {
            fields.put(field, value);
        }
        return JSON.writeValueAsString(fields);
    }
}
