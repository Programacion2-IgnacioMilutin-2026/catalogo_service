package catalogo.catedra.infrastructure.client.mapper;

import catalogo.catedra.domain.model.CatedraCategory;
import catalogo.catedra.domain.model.CatedraProfessional;
import catalogo.catedra.domain.model.CatedraWeeklySchedule;
import catalogo.catedra.infrastructure.client.dto.CatedraCategoryResponse;
import catalogo.catedra.infrastructure.client.dto.CatedraProfessionalResponse;
import catalogo.catedra.infrastructure.client.dto.CatedraWeeklyScheduleResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

// Qué datos y formatos tiene que traer cada entidad de la cátedra y cómo se convierte al modelo.
class CatedraEntityMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String WHERE = "entity 1";

    private static final String CATEGORY = """
            {"id":501,"name":"Kinesiología","description":"Rehabilitación física","enabled":true,
             "createdAt":"2026-07-01T12:00:00Z","updatedAt":"2026-09-10T13:40:00.123456Z"}""";
    private static final String PROFESSIONAL = """
            {"id":601,"categoryId":501,"firstName":"Valentina","lastName":"Ibáñez","enabled":false,
             "createdAt":"2026-07-01T12:00:00Z","updatedAt":"2026-09-10T13:40:00Z"}""";
    private static final String SCHEDULE = """
            {"id":701,"professionalId":601,"dayOfWeek":"MONDAY","startTime":"08:00:00","endTime":"12:00:00",
             "slotDurationMinutes":30,"enabled":true,"createdAt":"2026-07-01T12:00:00Z","updatedAt":"x"}""";

    private final CatedraEntityMapper mapper = new CatedraEntityMapper();

    @Test
    void validEntitiesHaveNoProblemsAndKeepEveryField() {
        CatedraCategoryResponse category = category(tree(CATEGORY));
        CatedraProfessionalResponse professional = professional(tree(PROFESSIONAL));
        CatedraWeeklyScheduleResponse schedule = schedule(tree(SCHEDULE));

        assertThat(mapper.problemsOf(category, WHERE)).isEmpty();
        assertThat(mapper.problemsOf(professional, WHERE)).isEmpty();
        assertThat(mapper.problemsOf(schedule, WHERE)).isEmpty();
        assertThat(mapper.toDomain(category))
                .isEqualTo(new CatedraCategory(501, "Kinesiología", "Rehabilitación física", true));
        assertThat(mapper.toDomain(professional))
                .isEqualTo(new CatedraProfessional(601, 501, "Valentina", "Ibáñez", false));
        assertThat(mapper.toDomain(schedule)).isEqualTo(new CatedraWeeklySchedule(701, 601, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(12, 0), 30, true));
    }

    @Test
    void anEmptyEntityIsAProblem() {
        assertThat(mapper.problemsOf((CatedraCategoryResponse) null, WHERE)).containsExactly(WHERE + ": empty");
        assertThat(mapper.problemsOf((CatedraProfessionalResponse) null, WHERE)).containsExactly(WHERE + ": empty");
        assertThat(mapper.problemsOf((CatedraWeeklyScheduleResponse) null, WHERE)).containsExactly(WHERE + ": empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "name", "enabled"})
    void aCategoryWithoutARequiredFieldIsAProblem(String field) {
        ObjectNode category = tree(CATEGORY);
        category.remove(field);

        assertThat(mapper.problemsOf(category(category), WHERE)).containsExactly(WHERE + ": missing " + field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "categoryId", "firstName", "lastName", "enabled"})
    void aProfessionalWithoutARequiredFieldIsAProblem(String field) {
        ObjectNode professional = tree(PROFESSIONAL);
        professional.remove(field);

        assertThat(mapper.problemsOf(professional(professional), WHERE)).containsExactly(WHERE + ": missing " + field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "professionalId", "slotDurationMinutes", "enabled"})
    void aScheduleWithoutARequiredFieldIsAProblem(String field) {
        ObjectNode schedule = tree(SCHEDULE);
        schedule.remove(field);

        assertThat(mapper.problemsOf(schedule(schedule), WHERE)).containsExactly(WHERE + ": missing " + field);
    }

    @Test
    void aBlankTextIsAProblem() {
        ObjectNode category = tree(CATEGORY);
        category.put("name", " ");
        ObjectNode professional = tree(PROFESSIONAL);
        professional.put("firstName", "").put("lastName", "  ");

        assertThat(mapper.problemsOf(category(category), WHERE)).containsExactly(WHERE + ": missing name");
        assertThat(mapper.problemsOf(professional(professional), WHERE))
                .containsExactly(WHERE + ": missing firstName", WHERE + ": missing lastName");
    }

    @ParameterizedTest
    @ValueSource(strings = {"monday", "FUNDAY", ""})
    void anInvalidDayOfWeekIsAProblem(String day) {
        ObjectNode schedule = tree(SCHEDULE);
        schedule.put("dayOfWeek", day);

        assertThat(mapper.problemsOf(schedule(schedule), WHERE)).containsExactly(WHERE + ": invalid dayOfWeek");
    }

    @Test
    void aMissingDayOfWeekIsAProblem() {
        ObjectNode schedule = tree(SCHEDULE);
        schedule.remove("dayOfWeek");

        assertThat(mapper.problemsOf(schedule(schedule), WHERE)).containsExactly(WHERE + ": invalid dayOfWeek");
    }

    @ParameterizedTest
    @CsvSource({"startTime, 9:00", "startTime, 09:00:00.5", "startTime, 25:00", "startTime, nueve",
            "endTime, 12-00", "endTime, ''"})
    void anInvalidTimeIsAProblem(String field, String value) {
        ObjectNode schedule = tree(SCHEDULE);
        schedule.put(field, value);

        assertThat(mapper.problemsOf(schedule(schedule), WHERE)).containsExactly(WHERE + ": invalid " + field);
    }

    @Test
    void aMissingTimeIsAProblem() {
        ObjectNode schedule = tree(SCHEDULE);
        schedule.remove("endTime");

        assertThat(mapper.problemsOf(schedule(schedule), WHERE)).containsExactly(WHERE + ": invalid endTime");
    }

    @Test
    void timesWithAndWithoutSecondsAreTheSameLocalTime() {
        ObjectNode schedule = tree(SCHEDULE);
        schedule.put("startTime", "08:00").put("endTime", "12:00:00");
        CatedraWeeklyScheduleResponse response = schedule(schedule);

        assertThat(mapper.problemsOf(response, WHERE)).isEmpty();
        assertThat(mapper.toDomain(response).startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(mapper.toDomain(response).endTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void aCategoryWithoutDescriptionIsAccepted() {
        ObjectNode category = tree(CATEGORY);
        category.remove("description");
        CatedraCategoryResponse response = category(category);

        assertThat(mapper.problemsOf(response, WHERE)).isEmpty();
        assertThat(mapper.toDomain(response).description()).isNull();
    }

    @Test
    void unknownFieldsAndInstantsInAnyFormatAreIgnored() {
        ObjectNode professional = tree(PROFESSIONAL);
        professional.put("createdAt", "ayer").put("extra", "value");
        professional.remove("updatedAt");
        CatedraProfessionalResponse response = professional(professional);

        assertThat(mapper.problemsOf(response, WHERE)).isEmpty();
        assertThat(mapper.toDomain(response)).isEqualTo(new CatedraProfessional(601, 501, "Valentina", "Ibáñez", false));
    }

    private static ObjectNode tree(String json) {
        return (ObjectNode) JSON.readTree(json);
    }

    private static CatedraCategoryResponse category(ObjectNode json) {
        return JSON.treeToValue(json, CatedraCategoryResponse.class);
    }

    private static CatedraProfessionalResponse professional(ObjectNode json) {
        return JSON.treeToValue(json, CatedraProfessionalResponse.class);
    }

    private static CatedraWeeklyScheduleResponse schedule(ObjectNode json) {
        return JSON.treeToValue(json, CatedraWeeklyScheduleResponse.class);
    }
}
