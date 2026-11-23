package catalogo.catalog.application.usecases;

import catalogo.catalog.application.exception.CatalogNotInitializedException;
import catalogo.catalog.domain.model.ProfessionalWithSchedules;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.in.GetProfessionalUseCase;
import catalogo.catalog.domain.ports.out.CatalogSyncStateRepository;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import catalogo.catalog.domain.ports.out.WeeklyScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// Lee un profesional con todos sus horarios.
@Component
@RequiredArgsConstructor
public class GetProfessionalUseCaseImpl implements GetProfessionalUseCase {

    private final CatalogSyncStateRepository catalogSyncStateRepository;
    private final ProfessionalRepository professionalRepository;
    private final WeeklyScheduleRepository weeklyScheduleRepository;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<ProfessionalWithSchedules> getProfessional(Long professionalId) {
        if (!catalogSyncStateRepository.get().isInitialized()) {
            throw new CatalogNotInitializedException();
        }
        return professionalRepository.findById(professionalId)
                .map(professional -> ProfessionalWithSchedules.builder()
                        .professional(professional)
                        .weeklySchedules(weeklyScheduleRepository.findAllByProfessionalIds(List.of(professionalId))
                                .stream()
                                .sorted(WeeklySchedule.BY_DAY_START_AND_ID)
                                .collect(Collectors.toList()))
                        .build());
    }
}
