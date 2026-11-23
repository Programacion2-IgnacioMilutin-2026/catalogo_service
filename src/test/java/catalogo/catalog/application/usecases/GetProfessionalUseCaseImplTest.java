package catalogo.catalog.application.usecases;

import catalogo.catalog.CatalogSamples;
import catalogo.catalog.application.exception.CatalogNotInitializedException;
import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalWithSchedules;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.out.CatalogSyncStateRepository;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import catalogo.catalog.domain.ports.out.WeeklyScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

// La consulta de un profesional con los puertos simulados.
@ExtendWith(MockitoExtension.class)
class GetProfessionalUseCaseImplTest {

    @Mock
    private CatalogSyncStateRepository catalogSyncStateRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private WeeklyScheduleRepository weeklyScheduleRepository;

    @InjectMocks
    private GetProfessionalUseCaseImpl getProfessional;

    private final Professional professional = CatalogSamples.professional(15, CatalogSamples.category(1));

    @Test
    void withoutALocalVersionItFailsWithoutLookingForTheProfessional() {
        given(catalogSyncStateRepository.get()).willReturn(new CatalogSyncState());

        assertThatThrownBy(() -> getProfessional.getProfessional(15L))
                .isInstanceOf(CatalogNotInitializedException.class);
        verifyNoInteractions(professionalRepository, weeklyScheduleRepository);
    }

    @Test
    void aProfessionalThatIsNotInTheCopyGivesNothingWithoutReadingSchedules() {
        initializedCatalog();
        given(professionalRepository.findById(999L)).willReturn(Optional.empty());

        assertThat(getProfessional.getProfessional(999L)).isEmpty();
        verifyNoInteractions(weeklyScheduleRepository);
    }

    @Test
    void theProfessionalComesWithAllItsSchedulesByDayStartAndId() {
        initializedCatalog();
        given(professionalRepository.findById(15L)).willReturn(Optional.of(professional));
        WeeklySchedule disabled = schedule(4, DayOfWeek.SUNDAY, 9);
        disabled.setEnabled(false);
        given(weeklyScheduleRepository.findAllByProfessionalIds(List.of(15L))).willReturn(List.of(
                schedule(3, DayOfWeek.FRIDAY, 10),
                disabled,
                schedule(2, DayOfWeek.MONDAY, 14),
                schedule(5, DayOfWeek.MONDAY, 8),
                schedule(1, DayOfWeek.MONDAY, 8)));

        Optional<ProfessionalWithSchedules> result = getProfessional.getProfessional(15L);

        assertThat(result).hasValueSatisfying(item -> {
            assertThat(item.getProfessional()).isSameAs(professional);
            assertThat(item.getWeeklySchedules()).extracting(WeeklySchedule::getId)
                    .containsExactly(1L, 5L, 2L, 3L, 4L);
        });
    }

    @Test
    void aProfessionalWithoutSchedulesComesWithAnEmptyList() {
        initializedCatalog();
        given(professionalRepository.findById(15L)).willReturn(Optional.of(professional));
        given(weeklyScheduleRepository.findAllByProfessionalIds(List.of(15L))).willReturn(List.of());

        assertThat(getProfessional.getProfessional(15L))
                .hasValueSatisfying(item -> assertThat(item.getWeeklySchedules()).isEmpty());
    }

    private void initializedCatalog() {
        given(catalogSyncStateRepository.get()).willReturn(CatalogSyncState.builder().localVersion(57L).build());
    }

    private WeeklySchedule schedule(long id, DayOfWeek day, int startHour) {
        WeeklySchedule schedule = CatalogSamples.weeklySchedule(id, professional);
        schedule.setDayOfWeek(day);
        schedule.setStartTime(LocalTime.of(startHour, 0));
        schedule.setEndTime(LocalTime.of(startHour + 2, 0));
        return schedule;
    }
}
