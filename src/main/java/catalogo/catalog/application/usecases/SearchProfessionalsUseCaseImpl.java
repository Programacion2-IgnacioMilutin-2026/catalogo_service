package catalogo.catalog.application.usecases;

import catalogo.catalog.application.exception.CatalogNotInitializedException;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalPage;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;
import catalogo.catalog.domain.model.ProfessionalWithSchedules;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.in.SearchProfessionalsUseCase;
import catalogo.catalog.domain.ports.out.CatalogSyncStateRepository;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import catalogo.catalog.domain.ports.out.WeeklyScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Busca una página de profesionales con sus horarios.
@Component
@RequiredArgsConstructor
public class SearchProfessionalsUseCaseImpl implements SearchProfessionalsUseCase {

    private final CatalogSyncStateRepository catalogSyncStateRepository;
    private final ProfessionalRepository professionalRepository;
    private final WeeklyScheduleRepository weeklyScheduleRepository;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProfessionalPage searchProfessionals(ProfessionalSearchCriteria criteria, int page, int size) {
        if (!catalogSyncStateRepository.get().isInitialized()) {
            throw new CatalogNotInitializedException();
        }
        long totalItems = professionalRepository.countMatching(criteria);
        if ((long) page * size >= totalItems) {
            return page(List.of(), totalItems);
        }
        List<Professional> professionals = professionalRepository.findMatching(criteria, page, size);
        Map<Long, List<WeeklySchedule>> schedulesByProfessional = weeklyScheduleRepository
                .findAllByProfessionalIds(professionals.stream().map(Professional::getId).collect(Collectors.toList()))
                .stream()
                .collect(Collectors.groupingBy(schedule -> schedule.getProfessional().getId()));
        return page(professionals.stream()
                .map(professional -> withSchedules(professional,
                        schedulesByProfessional.getOrDefault(professional.getId(), List.of())))
                .collect(Collectors.toList()), totalItems);
    }

    private static ProfessionalWithSchedules withSchedules(Professional professional, List<WeeklySchedule> schedules) {
        return ProfessionalWithSchedules.builder()
                .professional(professional)
                .weeklySchedules(schedules.stream().sorted(WeeklySchedule.BY_DAY_START_AND_ID).collect(Collectors.toList()))
                .build();
    }

    private static ProfessionalPage page(List<ProfessionalWithSchedules> professionals, long totalItems) {
        return ProfessionalPage.builder()
                .professionals(professionals)
                .totalItems(totalItems)
                .build();
    }
}
