package catalogo.catalog.application.usecases;

import catalogo.catalog.CatalogSamples;
import catalogo.catalog.application.exception.CatalogNotInitializedException;
import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.ProfessionalPage;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.out.CatalogSyncStateRepository;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import catalogo.catalog.domain.ports.out.WeeklyScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// La búsqueda de profesionales con los puertos simulados.
@ExtendWith(MockitoExtension.class)
class SearchProfessionalsUseCaseImplTest {

    private static final ProfessionalSearchCriteria CRITERIA = ProfessionalSearchCriteria.builder().name("ana").build();

    @Mock
    private CatalogSyncStateRepository catalogSyncStateRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private WeeklyScheduleRepository weeklyScheduleRepository;

    @InjectMocks
    private SearchProfessionalsUseCaseImpl searchProfessionals;

    private final ProfessionalCategory category = CatalogSamples.category(1);

    @Test
    void withoutALocalVersionItFailsWithoutSearching() {
        given(catalogSyncStateRepository.get()).willReturn(new CatalogSyncState());

        assertThatThrownBy(() -> searchProfessionals.searchProfessionals(CRITERIA, 0, 10))
                .isInstanceOf(CatalogNotInitializedException.class);
        verifyNoInteractions(professionalRepository, weeklyScheduleRepository);
    }

    @ParameterizedTest(name = "page={0}, size={1}, total={2}")
    @CsvSource({"3, 2, 5", "1, 5, 5", "0, 10, 0", "2000000000, 100, 5"})
    void aPageThatStartsAtTheTotalOrBeyondIsEmptyWithTheTotalAndReadsNothingElse(int page, int size, long total) {
        initializedCatalog();
        given(professionalRepository.countMatching(CRITERIA)).willReturn(total);

        ProfessionalPage result = searchProfessionals.searchProfessionals(CRITERIA, page, size);

        assertThat(result.getProfessionals()).isEmpty();
        assertThat(result.getTotalItems()).isEqualTo(total);
        verify(professionalRepository, never()).findMatching(any(), anyInt(), anyInt());
        verifyNoInteractions(weeklyScheduleRepository);
    }

    @Test
    void eachProfessionalOfThePageComesWithItsSchedulesByDayStartAndId() {
        Professional first = CatalogSamples.professional(15, category);
        Professional second = CatalogSamples.professional(16, category);
        initializedCatalog();
        given(professionalRepository.countMatching(CRITERIA)).willReturn(7L);
        given(professionalRepository.findMatching(CRITERIA, 1, 2)).willReturn(List.of(first, second));
        given(weeklyScheduleRepository.findAllByProfessionalIds(List.of(15L, 16L))).willReturn(List.of(
                schedule(3, first, DayOfWeek.FRIDAY, 10),
                schedule(2, first, DayOfWeek.MONDAY, 14),
                schedule(5, first, DayOfWeek.MONDAY, 8),
                schedule(1, first, DayOfWeek.MONDAY, 8)));

        ProfessionalPage result = searchProfessionals.searchProfessionals(CRITERIA, 1, 2);

        assertThat(result.getTotalItems()).isEqualTo(7);
        assertThat(result.getProfessionals()).extracting(item -> item.getProfessional().getId())
                .containsExactly(15L, 16L);
        assertThat(result.getProfessionals().getFirst().getWeeklySchedules())
                .extracting(WeeklySchedule::getId).containsExactly(1L, 5L, 2L, 3L);
        assertThat(result.getProfessionals().get(1).getWeeklySchedules()).isEmpty();
    }

    private void initializedCatalog() {
        given(catalogSyncStateRepository.get()).willReturn(CatalogSyncState.builder().localVersion(57L).build());
    }

    private static WeeklySchedule schedule(long id, Professional professional, DayOfWeek day, int startHour) {
        WeeklySchedule schedule = CatalogSamples.weeklySchedule(id, professional);
        schedule.setDayOfWeek(day);
        schedule.setStartTime(LocalTime.of(startHour, 0));
        schedule.setEndTime(LocalTime.of(startHour + 2, 0));
        return schedule;
    }
}
