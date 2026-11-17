package catalogo.catedra.infrastructure.client.mapper;

import catalogo.catedra.CatalogSnapshots;
import catalogo.catedra.FakeCatedraServer;
import catalogo.catedra.domain.model.CatalogSnapshot;
import catalogo.catedra.infrastructure.client.dto.CatalogSnapshotResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

// Qué datos y formatos tiene que traer el snapshot, y cómo se convierte al modelo.
class CatalogSnapshotMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CatalogSnapshotMapper mapper = new CatalogSnapshotMapper();

    @Test
    void theRecordedSnapshotIsReadCompletely() {
        CatalogSnapshotResponse response = read(FakeCatedraServer.snapshotTree());

        assertThat(mapper.problemsOf(response)).isEmpty();
        assertThat(mapper.toDomain(response)).isEqualTo(CatalogSnapshots.valid());
    }

    @Test
    void anEmptyResponseIsAProblem() {
        assertThat(mapper.problemsOf(null)).containsExactly("empty response");
    }

    @Test
    void aMissingVersionIsAProblem() {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        snapshot.remove("snapshotVersion");

        assertThat(mapper.problemsOf(read(snapshot))).containsExactly("missing snapshotVersion");
    }

    @ParameterizedTest
    @ValueSource(strings = {"professionalCategories", "professionals", "weeklySchedules"})
    void aMissingCollectionIsAProblem(String collection) {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        snapshot.remove(collection);

        assertThat(mapper.problemsOf(read(snapshot))).containsExactly("missing " + collection);
    }

    @ParameterizedTest
    @CsvSource({
            "professionalCategories, id", "professionalCategories, name", "professionalCategories, enabled",
            "professionals, id", "professionals, categoryId", "professionals, firstName",
            "professionals, lastName", "professionals, enabled",
            "weeklySchedules, id", "weeklySchedules, professionalId", "weeklySchedules, slotDurationMinutes",
            "weeklySchedules, enabled"})
    void aMissingRequiredFieldIsAProblem(String collection, String field) {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        firstOf(snapshot, collection).remove(field);

        assertThat(mapper.problemsOf(read(snapshot))).containsExactly(collection + "[0]: missing " + field);
    }

    @ParameterizedTest
    @CsvSource({"professionalCategories, name", "professionals, firstName", "professionals, lastName"})
    void aBlankTextIsAProblem(String collection, String field) {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        firstOf(snapshot, collection).put(field, "  ");

        assertThat(mapper.problemsOf(read(snapshot))).containsExactly(collection + "[0]: missing " + field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"monday", "FUNDAY", ""})
    void anInvalidDayOfWeekIsAProblem(String day) {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        firstOf(snapshot, "weeklySchedules").put("dayOfWeek", day);

        assertThat(mapper.problemsOf(read(snapshot))).containsExactly("weeklySchedules[0]: invalid dayOfWeek");
    }

    @Test
    void aMissingDayOfWeekIsAProblem() {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        firstOf(snapshot, "weeklySchedules").remove("dayOfWeek");

        assertThat(mapper.problemsOf(read(snapshot))).containsExactly("weeklySchedules[0]: invalid dayOfWeek");
    }

    @ParameterizedTest
    @CsvSource({"startTime, 9:00", "startTime, 09:00:00.5", "startTime, 25:00", "startTime, nueve",
            "endTime, 12-00", "endTime, ''"})
    void anInvalidTimeIsAProblem(String field, String value) {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        firstOf(snapshot, "weeklySchedules").put(field, value);

        assertThat(mapper.problemsOf(read(snapshot))).containsExactly("weeklySchedules[0]: invalid " + field);
    }

    @Test
    void anEmptyElementIsAProblem() {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        ((ArrayNode) snapshot.get("professionals")).addNull();

        assertThat(mapper.problemsOf(read(snapshot))).containsExactly("professionals[3]: empty");
    }

    @Test
    void timesWithAndWithoutSecondsAreTheSameLocalTime() {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        firstOf(snapshot, "weeklySchedules").put("startTime", "08:00");
        firstOf(snapshot, "weeklySchedules").put("endTime", "12:00:00");
        CatalogSnapshotResponse response = read(snapshot);

        assertThat(mapper.problemsOf(response)).isEmpty();
        CatalogSnapshot result = mapper.toDomain(response);
        assertThat(result.weeklySchedules().getFirst().startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(result.weeklySchedules().getFirst().endTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void aCategoryWithoutDescriptionIsAccepted() {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        firstOf(snapshot, "professionalCategories").remove("description");
        CatalogSnapshotResponse response = read(snapshot);

        assertThat(mapper.problemsOf(response)).isEmpty();
        assertThat(mapper.toDomain(response).categories().getFirst().description()).isNull();
    }

    @Test
    void unknownFieldsAndInstantsInAnyFormatAreIgnored() {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        snapshot.put("generatedAt", "not an instant");
        snapshot.put("somethingNew", 7);
        firstOf(snapshot, "professionals").put("createdAt", "ayer").put("extra", "value");
        firstOf(snapshot, "weeklySchedules").remove("updatedAt");
        CatalogSnapshotResponse response = read(snapshot);

        assertThat(mapper.problemsOf(response)).isEmpty();
        assertThat(mapper.toDomain(response)).isEqualTo(CatalogSnapshots.valid());
    }

    @Test
    void emptyCollectionsAreNotAFormatProblem() {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        snapshot.putArray("professionals");
        snapshot.putArray("weeklySchedules");

        assertThat(mapper.problemsOf(read(snapshot))).isEmpty();
    }

    private static ObjectNode firstOf(ObjectNode snapshot, String collection) {
        return (ObjectNode) snapshot.get(collection).get(0);
    }

    private static CatalogSnapshotResponse read(JsonNode snapshot) {
        return JSON.treeToValue(snapshot, CatalogSnapshotResponse.class);
    }
}
