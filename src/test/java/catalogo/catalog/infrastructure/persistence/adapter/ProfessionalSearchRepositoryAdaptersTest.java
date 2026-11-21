package catalogo.catalog.infrastructure.persistence.adapter;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import catalogo.catalog.domain.ports.out.WeeklyScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// La búsqueda de profesionales contra un PostgreSQL real.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProfessionalSearchRepositoryAdaptersTest {

    private static final ProfessionalSearchCriteria NO_FILTERS = new ProfessionalSearchCriteria();

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private WeeklyScheduleRepository weeklyScheduleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CatalogTables tables;

    @BeforeEach
    void catalogWithOneCategory() {
        tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        tables.insertCategory(1, "Kinesiología", null, true);
    }

    @Test
    void professionalsComeInSpanishAlphabeticalOrderByLastNameFirstNameAndId() {
        tables.insertProfessional(1, 1, "Ana", "Zárate", true);
        tables.insertProfessional(2, 1, "Ana", "Muñoz", true);
        tables.insertProfessional(3, 1, "Bruno", "Gómez", false);
        tables.insertProfessional(4, 1, "Ana", "Munro", true);
        tables.insertProfessional(5, 1, "Ana", "Gómez", true);
        tables.insertProfessional(6, 1, "ana", "gomez", true);
        tables.insertProfessional(7, 1, "Ana", "de la Fuente", false);
        tables.insertProfessional(8, 1, "Ana", "Álvarez", true);
        tables.insertProfessional(9, 1, "Lucía", "Núñez", true);

        List<Professional> found = professionalRepository.findMatching(NO_FILTERS, 0, 100);

        assertThat(found).extracting(Professional::getId).containsExactly(8L, 7L, 5L, 6L, 3L, 4L, 2L, 9L, 1L);
        assertThat(found.getFirst())
                .returns("Ana", Professional::getFirstName)
                .returns("Álvarez", Professional::getLastName)
                .returns(true, Professional::isEnabled);
        assertThat(found.getFirst().getCategory().getName()).isEqualTo("Kinesiología");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @MethodSource("nameSearches")
    void everyWordOfTheNameIsInTheFirstOrLastNameIgnoringCaseAndAccents(String name, List<Long> expectedIds) {
        tables.insertProfessional(1, 1, "Tomás", "Ferreyra", true);
        tables.insertProfessional(2, 1, "Valentina", "Ibáñez", true);
        tables.insertProfessional(3, 1, "Lucía", "Núñez", true);
        tables.insertProfessional(4, 1, "Mariana", "Paz", true);
        ProfessionalSearchCriteria criteria = ProfessionalSearchCriteria.builder().name(name).build();

        List<Professional> found = professionalRepository.findMatching(criteria, 0, 100);

        assertThat(found).extracting(Professional::getId).containsExactlyElementsOf(expectedIds);
        assertThat(professionalRepository.countMatching(criteria)).isEqualTo(expectedIds.size());
    }

    static Stream<Arguments> nameSearches() {
        return Stream.of(
                arguments("tomás ferreyra", List.of(1L)),
                arguments("ferreyra tomás", List.of(1L)),
                arguments("TOM ferr", List.of(1L)),
                arguments("  tomas  ", List.of(1L)),
                arguments("tomás gómez", List.of()),
                arguments("nunez", List.of(3L)),
                arguments("nuñez", List.of(3L)),
                arguments("NÚÑEZ", List.of(3L)),
                arguments("ibanez", List.of(2L)),
                arguments("ana", List.of(4L)),
                arguments("%", List.of()),
                arguments("_", List.of()),
                arguments("\\", List.of()));
    }

    @Test
    void withoutFiltersEveryProfessionalIsCounted() {
        tables.insertProfessional(1, 1, "Tomás", "Ferreyra", true);
        tables.insertProfessional(2, 1, "Valentina", "Ibáñez", false);

        assertThat(professionalRepository.countMatching(NO_FILTERS)).isEqualTo(2);
    }

    @ParameterizedTest(name = "categoryId={0}, enabled={1}")
    @MethodSource("categoryAndStateFilters")
    void theCategoryAndTheStateFilterByTheProfessionalOnly(Long categoryId, Boolean enabled, List<Long> expectedIds) {
        tables.insertCategory(2, "Nutrición", null, false);
        tables.insertProfessional(1, 1, "Ana", "Acosta", true);
        tables.insertProfessional(2, 1, "Bruno", "Benítez", false);
        tables.insertProfessional(3, 2, "Carla", "Castro", true);
        tables.insertProfessional(4, 2, "Diego", "Díaz", false);
        tables.insertWeeklySchedule(10, 1, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, false);
        ProfessionalSearchCriteria criteria = ProfessionalSearchCriteria.builder()
                .categoryId(categoryId)
                .enabled(enabled)
                .build();

        List<Professional> found = professionalRepository.findMatching(criteria, 0, 100);

        assertThat(found).extracting(Professional::getId).containsExactlyElementsOf(expectedIds);
        assertThat(professionalRepository.countMatching(criteria)).isEqualTo(expectedIds.size());
    }

    static Stream<Arguments> categoryAndStateFilters() {
        return Stream.of(
                arguments(1L, null, List.of(1L, 2L)),
                arguments(2L, null, List.of(3L, 4L)),
                arguments(null, true, List.of(1L, 3L)),
                arguments(null, false, List.of(2L, 4L)),
                arguments(2L, false, List.of(4L)),
                arguments(99L, null, List.of()));
    }

    @Test
    void theNameCombinesWithTheCategoryAndTheState() {
        tables.insertCategory(2, "Nutrición", null, true);
        tables.insertProfessional(1, 2, "Carla", "Castro", true);
        tables.insertProfessional(2, 2, "Carla", "Acosta", true);
        tables.insertProfessional(3, 1, "Carla", "Rey", true);
        tables.insertProfessional(4, 2, "Carla", "Soto", false);
        tables.insertProfessional(5, 2, "Diego", "Ruiz", true);
        ProfessionalSearchCriteria criteria = ProfessionalSearchCriteria.builder()
                .name("carla")
                .categoryId(2L)
                .enabled(true)
                .build();

        assertThat(professionalRepository.findMatching(criteria, 0, 100))
                .extracting(Professional::getId).containsExactly(2L, 1L);
        assertThat(professionalRepository.countMatching(criteria)).isEqualTo(2);
    }

    @Test
    void pagesFollowTheOrderWithoutRepeatsOrGaps() {
        tables.insertProfessional(1, 1, "Ana", "Echeverría", true);
        tables.insertProfessional(2, 1, "Ana", "Acosta", true);
        tables.insertProfessional(3, 1, "Ana", "Dávila", false);
        tables.insertProfessional(4, 1, "Ana", "Castro", true);
        tables.insertProfessional(5, 1, "Ana", "Benítez", true);

        assertThat(professionalRepository.countMatching(NO_FILTERS)).isEqualTo(5);
        assertThat(professionalRepository.findMatching(NO_FILTERS, 0, 2))
                .extracting(Professional::getId).containsExactly(2L, 5L);
        assertThat(professionalRepository.findMatching(NO_FILTERS, 1, 2))
                .extracting(Professional::getId).containsExactly(4L, 3L);
        assertThat(professionalRepository.findMatching(NO_FILTERS, 2, 2))
                .extracting(Professional::getId).containsExactly(1L);
        assertThat(professionalRepository.findMatching(NO_FILTERS, 3, 2)).isEmpty();
    }

    @Test
    void withoutProfessionalsNothingIsFound() {
        assertThat(professionalRepository.findMatching(NO_FILTERS, 0, 10)).isEmpty();
        assertThat(professionalRepository.countMatching(NO_FILTERS)).isZero();
    }

    @Test
    void onlyAnEnabledScheduleOnTheDayThatOverlapsTheRangeCounts() {
        tables.insertProfessional(1, 1, "Ana", "Acosta", true);
        tables.insertWeeklySchedule(10, 1, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, true);
        tables.insertWeeklySchedule(11, 1, DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(18, 0), 30, true);
        tables.insertProfessional(2, 1, "Bruno", "Benítez", true);
        tables.insertWeeklySchedule(20, 2, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, false);

        assertThat(professionalRepository.findMatching(
                availability(Set.of(DayOfWeek.MONDAY), LocalTime.of(14, 0), LocalTime.of(18, 0)), 0, 100)).isEmpty();
        assertThat(professionalRepository.findMatching(
                availability(Set.of(DayOfWeek.MONDAY), LocalTime.of(9, 0), LocalTime.of(11, 0)), 0, 100))
                .extracting(Professional::getId).containsExactly(1L);
    }

    @ParameterizedTest(name = "días={0}, from={1}, to={2}")
    @MethodSource("availabilitySearches")
    void eachPartOfTheAvailabilityAppliesOnItsOwn(Set<DayOfWeek> days, LocalTime from, LocalTime to,
                                                    List<Long> expectedIds) {
        tables.insertProfessional(1, 1, "Ana", "Acosta", true);
        tables.insertWeeklySchedule(10, 1, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, true);
        tables.insertProfessional(2, 1, "Bruno", "Benítez", true);
        tables.insertWeeklySchedule(20, 2, DayOfWeek.FRIDAY, LocalTime.of(10, 0), LocalTime.of(14, 0), 20, true);
        tables.insertProfessional(3, 1, "Carla", "Castro", true);
        tables.insertWeeklySchedule(30, 3, DayOfWeek.MONDAY, LocalTime.of(22, 0), LocalTime.of(2, 0), 30, true);
        tables.insertProfessional(4, 1, "Diego", "Díaz", true);
        ProfessionalSearchCriteria criteria = availability(days, from, to);

        assertThat(professionalRepository.findMatching(criteria, 0, 100))
                .extracting(Professional::getId).containsExactlyElementsOf(expectedIds);
        assertThat(professionalRepository.countMatching(criteria)).isEqualTo(expectedIds.size());
    }

    static Stream<Arguments> availabilitySearches() {
        return Stream.of(
                arguments(Set.of(DayOfWeek.MONDAY), null, null, List.of(1L)),
                arguments(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), null, null, List.of(1L, 2L)),
                arguments(null, LocalTime.of(13, 0), LocalTime.of(15, 0), List.of(2L)),
                arguments(null, LocalTime.of(13, 0), null, List.of(2L)),
                arguments(null, null, LocalTime.of(9, 0), List.of(1L)),
                arguments(null, null, LocalTime.of(23, 0), List.of(1L, 2L)),
                arguments(null, LocalTime.of(12, 0), null, List.of(2L)),
                arguments(null, null, LocalTime.of(8, 0), List.of()),
                arguments(null, LocalTime.of(11, 30), null, List.of(1L, 2L)),
                arguments(Set.of(), null, null, List.of(1L, 2L, 3L, 4L)),
                arguments(null, null, null, List.of(1L, 2L, 3L, 4L)));
    }

    @Test
    void theAvailabilityCombinesWithTheName() {
        tables.insertProfessional(1, 1, "Ana", "Acosta", true);
        tables.insertWeeklySchedule(10, 1, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, true);
        tables.insertProfessional(2, 1, "Ana", "Benítez", true);
        tables.insertWeeklySchedule(20, 2, DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, true);
        tables.insertProfessional(3, 1, "Bruno", "Castro", true);
        tables.insertWeeklySchedule(30, 3, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, true);
        ProfessionalSearchCriteria criteria = ProfessionalSearchCriteria.builder()
                .name("ana")
                .daysOfWeek(Set.of(DayOfWeek.MONDAY))
                .build();

        assertThat(professionalRepository.findMatching(criteria, 0, 100))
                .extracting(Professional::getId).containsExactly(1L);
    }

    @Test
    void theSchedulesOfTheRequestedProfessionalsAreRead() {
        tables.insertProfessional(1, 1, "Ana", "Acosta", true);
        tables.insertWeeklySchedule(10, 1, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, true);
        tables.insertWeeklySchedule(11, 1, DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(18, 0), 20, false);
        tables.insertProfessional(2, 1, "Bruno", "Benítez", true);
        tables.insertWeeklySchedule(20, 2, DayOfWeek.FRIDAY, LocalTime.of(10, 0), LocalTime.of(14, 0), 20, true);
        tables.insertProfessional(3, 1, "Carla", "Castro", true);
        tables.insertWeeklySchedule(30, 3, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), 30, true);

        List<WeeklySchedule> found = weeklyScheduleRepository.findAllByProfessionalIds(List.of(1L, 3L));

        assertThat(found).extracting(WeeklySchedule::getId).containsExactlyInAnyOrder(10L, 11L, 30L);
        assertThat(found).filteredOn(schedule -> schedule.getId() == 11L).singleElement().satisfies(schedule -> {
            assertThat(schedule.getProfessional().getId()).isEqualTo(1L);
            assertThat(schedule.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
            assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(14, 0));
            assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(18, 0));
            assertThat(schedule.getSlotDurationMinutes()).isEqualTo(20);
            assertThat(schedule.isEnabled()).isFalse();
        });
        assertThat(weeklyScheduleRepository.findAllByProfessionalIds(List.of())).isEmpty();
    }

    private static ProfessionalSearchCriteria availability(Set<DayOfWeek> days, LocalTime from, LocalTime to) {
        return ProfessionalSearchCriteria.builder()
                .daysOfWeek(days)
                .from(from)
                .to(to)
                .build();
    }
}
