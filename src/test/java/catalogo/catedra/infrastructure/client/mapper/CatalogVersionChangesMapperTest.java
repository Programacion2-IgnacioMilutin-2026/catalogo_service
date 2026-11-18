package catalogo.catedra.infrastructure.client.mapper;

import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogVersionChanges;
import catalogo.catedra.domain.model.CatedraCategory;
import catalogo.catedra.domain.model.CatedraProfessional;
import catalogo.catedra.domain.model.RedisReadResult;
import catalogo.catedra.infrastructure.client.dto.CatalogVersionChangesResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Qué tiene que traer lo que la cátedra publica en Redis, y cómo se convierte al modelo.
class CatalogVersionChangesMapperTest {

    private static final String CATEGORY_502 = """
            {"id":502,"name":"Nutrición","description":"Planes de alimentación","enabled":true,
             "createdAt":"2026-07-01T12:00:00Z","updatedAt":"2026-09-10T13:40:00.123456Z"}""";
    private static final String PROFESSIONAL_601 = """
            {"id":601,"categoryId":501,"firstName":"Valentina","lastName":"Ibáñez","enabled":true,
             "createdAt":"2026-07-01T12:00:00Z","updatedAt":"2026-09-10T13:40:00Z"}""";
    private static final String SCHEDULE_702 = """
            {"id":702,"professionalId":601,"dayOfWeek":"THURSDAY","startTime":"14:00","endTime":"18:00:00",
             "slotDurationMinutes":30,"enabled":false,"createdAt":"x","updatedAt":"y"}""";

    private final CatalogVersionChangesMapper mapper = new CatalogVersionChangesMapper(new CatedraEntityMapper());

    @Test
    void validMetadataIsRead() {
        assertThat(mapper.metadataOf(Map.of("currentVersion", "333", "oldestAvailableVersion", "234",
                "publishedAt", "2026-11-18T04:40:15.123456789Z", "somethingNew", "x")))
                .isEqualTo(new RedisReadResult.Received<>(new CatalogMetadata(333, 234)));
        assertThat(mapper.metadataOf(Map.of("currentVersion", "234", "oldestAvailableVersion", "234")))
                .isEqualTo(new RedisReadResult.Received<>(new CatalogMetadata(234, 234)));
    }

    @Test
    void missingMetadataIsInvalid() {
        assertThat(mapper.metadataOf(Map.of())).isInstanceOf(RedisReadResult.Invalid.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"currentVersion", "oldestAvailableVersion"})
    void aMissingVersionInTheMetadataIsInvalid(String field) {
        Map<String, String> metadata = new HashMap<>(Map.of("currentVersion", "333", "oldestAvailableVersion", "234"));
        metadata.remove(field);

        assertThat(invalidReason(mapper.metadataOf(metadata))).contains("missing " + field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-1", "3.5", "", "99999999999999999999"})
    void aVersionThatIsNotANonNegativeIntegerIsInvalid(String value) {
        assertThat(invalidReason(mapper.metadataOf(Map.of("currentVersion", value, "oldestAvailableVersion", "1"))))
                .contains("invalid currentVersion");
    }

    @Test
    void aCurrentVersionLowerThanTheOldestIsInvalid() {
        assertThat(invalidReason(mapper.metadataOf(Map.of("currentVersion", "10", "oldestAvailableVersion", "11"))))
                .contains("currentVersion 10 is lower than oldestAvailableVersion 11");
    }

    @Test
    void validChangesAreRead() {
        RedisReadResult<CatalogVersionChangesResponse> result = mapper.changesOf(59,
                "{\"version\":59,\"changes\":{\"professionalCategories\":[502],\"professionals\":[601,601],"
                        + "\"weeklySchedules\":[],\"somethingNew\":[1]},\"extra\":true}");

        assertThat(result).isInstanceOf(RedisReadResult.Received.class);
        CatalogVersionChangesResponse changes = ((RedisReadResult.Received<CatalogVersionChangesResponse>) result).value();
        assertThat(CatalogVersionChangesMapper.distinctIds(changes.getChanges().getProfessionals())).containsExactly(601L);
    }

    @Test
    void missingChangesAreInvalid() {
        assertThat(invalidReason(mapper.changesOf(59, null))).contains("do not exist");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "{\"version\":59,\"changes\":{\"professionalCategories\":[],\"professionals\":[\"abc\"],\"weeklySchedules\":[]}}"})
    void unreadableChangesAreInvalid(String json) {
        assertThat(invalidReason(mapper.changesOf(59, json))).contains("cannot be read");
    }

    @Test
    void changesOfAnotherVersionAreInvalid() {
        assertThat(invalidReason(mapper.changesOf(59, "{\"version\":58,\"changes\":{\"professionalCategories\":[],"
                + "\"professionals\":[],\"weeklySchedules\":[]}}"))).contains("version 58 does not match");
    }

    @ParameterizedTest
    @ValueSource(strings = {"professionalCategories", "professionals", "weeklySchedules"})
    void changesWithoutACollectionAreInvalid(String collection) {
        String json = "{\"version\":59,\"changes\":{" + switch (collection) {
            case "professionalCategories" -> "\"professionals\":[],\"weeklySchedules\":[]";
            case "professionals" -> "\"professionalCategories\":[],\"weeklySchedules\":[]";
            default -> "\"professionalCategories\":[],\"professionals\":[]";
        } + "}}";

        assertThat(invalidReason(mapper.changesOf(59, json))).contains("missing " + collection);
    }

    @Test
    void changesWithoutVersionOrChangesOrWithAnEmptyIdAreInvalid() {
        assertThat(invalidReason(mapper.changesOf(59, "{\"changes\":{\"professionalCategories\":[],"
                + "\"professionals\":[],\"weeklySchedules\":[]}}"))).contains("missing version");
        assertThat(invalidReason(mapper.changesOf(59, "{\"version\":59}"))).contains("missing changes");
        assertThat(invalidReason(mapper.changesOf(59, "{\"version\":59,\"changes\":{\"professionalCategories\":[],"
                + "\"professionals\":[null],\"weeklySchedules\":[]}}"))).contains("professionals has an empty id");
        assertThat(invalidReason(mapper.changesOf(59, "null"))).contains("empty");
    }

    @Test
    void theEntitiesOfAVersionAreReadAndConverted() {
        CatalogVersionChangesResponse changes = changes("[502]", "[601]", "[702]");

        RedisReadResult<CatalogVersionChanges> result = mapper.versionChangesOf(59, changes,
                Map.of(502L, CATEGORY_502), Map.of(601L, PROFESSIONAL_601), Map.of(702L, SCHEDULE_702));

        assertThat(result).isInstanceOf(RedisReadResult.Received.class);
        CatalogVersionChanges version = ((RedisReadResult.Received<CatalogVersionChanges>) result).value();
        assertThat(version.version()).isEqualTo(59);
        assertThat(version.categories())
                .containsExactly(new CatedraCategory(502, "Nutrición", "Planes de alimentación", true));
        assertThat(version.professionals()).containsExactly(new CatedraProfessional(601, 501, "Valentina", "Ibáñez", true));
        assertThat(version.weeklySchedules().getFirst().startTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(version.weeklySchedules().getFirst().endTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(version.weeklySchedules().getFirst().enabled()).isFalse();
    }

    @Test
    void anEntityThatIsNotInItsHashIsInvalid() {
        Map<Long, String> professionals = new HashMap<>();
        professionals.put(601L, null);

        assertThat(invalidReason(mapper.versionChangesOf(59, changes("[]", "[601]", "[]"), Map.of(), professionals,
                Map.of()))).contains("professionals 601: not in its hash");
    }

    @Test
    void anUnreadableEntityIsInvalid() {
        assertThat(invalidReason(mapper.versionChangesOf(59, changes("[502]", "[]", "[]"), Map.of(502L, "{nope"),
                Map.of(), Map.of()))).contains("professionalCategories 502: unreadable");
    }

    @Test
    void anEntityWithADefectIsInvalid() {
        String withoutLastName = PROFESSIONAL_601.replace("\"lastName\":\"Ibáñez\",", "");
        String badDay = SCHEDULE_702.replace("THURSDAY", "thursday");

        assertThat(invalidReason(mapper.versionChangesOf(59, changes("[]", "[601]", "[702]"), Map.of(),
                Map.of(601L, withoutLastName), Map.of(702L, badDay))))
                .contains("professionals 601: missing lastName", "weeklySchedules 702: invalid dayOfWeek");
    }

    @Test
    void anEntityWhoseIdDoesNotMatchItsFieldIsInvalid() {
        assertThat(invalidReason(mapper.versionChangesOf(59, changes("[]", "[602]", "[]"), Map.of(),
                Map.of(602L, PROFESSIONAL_601), Map.of()))).contains("professionals 602: id does not match its field");
    }

    @Test
    void aCategoryWithoutDescriptionAndUnknownFieldsIsAccepted() {
        String withoutDescription = CATEGORY_502.replace("\"description\":\"Planes de alimentación\",",
                "\"extra\":1,");

        RedisReadResult<CatalogVersionChanges> result = mapper.versionChangesOf(59, changes("[502]", "[]", "[]"),
                Map.of(502L, withoutDescription), Map.of(), Map.of());

        assertThat(result).isInstanceOf(RedisReadResult.Received.class);
        assertThat(((RedisReadResult.Received<CatalogVersionChanges>) result).value().categories().getFirst()
                .description()).isNull();
    }

    private CatalogVersionChangesResponse changes(String categories, String professionals, String schedules) {
        RedisReadResult<CatalogVersionChangesResponse> result = mapper.changesOf(59, "{\"version\":59,\"changes\":{"
                + "\"professionalCategories\":" + categories + ",\"professionals\":" + professionals
                + ",\"weeklySchedules\":" + schedules + "}}");
        return ((RedisReadResult.Received<CatalogVersionChangesResponse>) result).value();
    }

    private static String invalidReason(RedisReadResult<?> result) {
        assertThat(result).isInstanceOf(RedisReadResult.Invalid.class);
        return ((RedisReadResult.Invalid<?>) result).reason();
    }
}
