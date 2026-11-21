package catalogo.catalog.infrastructure.web.mapper;

import catalogo.catalog.CatalogSamples;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.ProfessionalPage;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;
import catalogo.catalog.domain.model.ProfessionalWithSchedules;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.infrastructure.web.dto.ProfessionalPageResponse;
import catalogo.catalog.infrastructure.web.dto.ProfessionalResponse;
import catalogo.catalog.infrastructure.web.dto.ProfessionalSearchRequest;
import catalogo.catalog.infrastructure.web.dto.WeeklyScheduleResponse;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Las conversiones del mapper web de la búsqueda de profesionales.
class ProfessionalDtoMapperTest {

    private final ProfessionalDtoMapper mapper = new ProfessionalDtoMapper();

    @Test
    void aFullRequestBecomesTheFilters() {
        ProfessionalSearchRequest request = ProfessionalSearchRequest.builder()
                .categoryId("5")
                .enabled("false")
                .name("  tomás   ferr ")
                .dayOfWeek(List.of("monday", "FRIDAY", "Monday"))
                .from("09:00")
                .to("13:30:00")
                .page("3")
                .size("25")
                .build();

        ProfessionalSearchCriteria criteria = mapper.toDomain(request);

        assertThat(criteria.getCategoryId()).isEqualTo(5L);
        assertThat(criteria.getEnabled()).isFalse();
        assertThat(criteria.getName()).isEqualTo("tomás   ferr");
        assertThat(criteria.nameWords()).containsExactly("tomás", "ferr");
        assertThat(criteria.getDaysOfWeek()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(criteria.getFrom()).isEqualTo(LocalTime.of(9, 0));
        assertThat(criteria.getTo()).isEqualTo(LocalTime.of(13, 30));
        assertThat(mapper.toPage(request)).isEqualTo(3);
        assertThat(mapper.toSize(request)).isEqualTo(25);
    }

    @Test
    void emptyParametersCountAsMissing() {
        ProfessionalSearchRequest request = ProfessionalSearchRequest.builder()
                .categoryId("")
                .enabled("")
                .name("   ")
                .dayOfWeek(List.of("", ""))
                .from("")
                .to("")
                .page("")
                .size("")
                .build();

        assertThatHasNoFilters(mapper.toDomain(request));
        assertThat(mapper.toPage(request)).isZero();
        assertThat(mapper.toSize(request)).isEqualTo(10);
    }

    @Test
    void missingParametersCountAsNoFiltersWithTheDefaultPage() {
        ProfessionalSearchRequest request = new ProfessionalSearchRequest();

        assertThatHasNoFilters(mapper.toDomain(request));
        assertThat(mapper.toPage(request)).isZero();
        assertThat(mapper.toSize(request)).isEqualTo(10);
    }

    @Test
    void anEmptyDayNextToAnotherIsIgnored() {
        ProfessionalSearchRequest request = ProfessionalSearchRequest.builder()
                .dayOfWeek(List.of("MONDAY", ""))
                .build();

        assertThat(mapper.toDomain(request).getDaysOfWeek()).containsExactly(DayOfWeek.MONDAY);
    }

    @Test
    void thePageKeepsEveryFieldOfEachProfessional() {
        ProfessionalCategory category = CatalogSamples.category(1);
        Professional professional = CatalogSamples.professional(15, category);
        WeeklySchedule schedule = CatalogSamples.weeklySchedule(21, professional);
        ProfessionalPage page = ProfessionalPage.builder()
                .professionals(List.of(ProfessionalWithSchedules.builder()
                        .professional(professional)
                        .weeklySchedules(List.of(schedule))
                        .build()))
                .totalItems(31)
                .build();

        ProfessionalPageResponse response = mapper.toResponse(page, 2, 10);

        assertThat(response).hasNoNullFieldsOrProperties();
        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalItems()).isEqualTo(31);
        ProfessionalResponse item = response.getItems().getFirst();
        assertThat(item).hasNoNullFieldsOrProperties()
                .returns(15L, ProfessionalResponse::getId)
                .returns(professional.getFirstName(), ProfessionalResponse::getFirstName)
                .returns(professional.getLastName(), ProfessionalResponse::getLastName)
                .returns(true, ProfessionalResponse::isEnabled);
        assertThat(item.getCategory()).hasNoNullFieldsOrProperties();
        assertThat(item.getCategory().getId()).isEqualTo(1L);
        assertThat(item.getCategory().getName()).isEqualTo(category.getName());
        assertThat(item.getCategory().isEnabled()).isTrue();
        assertThat(item.getWeeklySchedules()).singleElement().satisfies(scheduleResponse -> assertThat(scheduleResponse)
                .hasNoNullFieldsOrProperties()
                .returns(21L, WeeklyScheduleResponse::getId)
                .returns(DayOfWeek.MONDAY, WeeklyScheduleResponse::getDayOfWeek)
                .returns(LocalTime.of(9, 0), WeeklyScheduleResponse::getStartTime)
                .returns(LocalTime.of(12, 0), WeeklyScheduleResponse::getEndTime)
                .returns(30, WeeklyScheduleResponse::getSlotDurationMinutes)
                .returns(true, WeeklyScheduleResponse::isEnabled));
    }

    @Test
    void aProfessionalWithoutSchedulesHasAnEmptyList() {
        ProfessionalPage page = ProfessionalPage.builder()
                .professionals(List.of(ProfessionalWithSchedules.builder()
                        .professional(CatalogSamples.professional(15, CatalogSamples.category(1)))
                        .weeklySchedules(List.of())
                        .build()))
                .totalItems(1)
                .build();

        assertThat(mapper.toResponse(page, 0, 10).getItems().getFirst().getWeeklySchedules()).isEmpty();
    }

    @Test
    void nullMapsToNull() {
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toResponse(null, 0, 10)).isNull();
    }

    private static void assertThatHasNoFilters(ProfessionalSearchCriteria criteria) {
        assertThat(criteria.getCategoryId()).isNull();
        assertThat(criteria.getEnabled()).isNull();
        assertThat(criteria.getName()).isNull();
        assertThat(criteria.nameWords()).isEmpty();
        assertThat(criteria.getDaysOfWeek()).isEmpty();
        assertThat(criteria.getFrom()).isNull();
        assertThat(criteria.getTo()).isNull();
    }
}
