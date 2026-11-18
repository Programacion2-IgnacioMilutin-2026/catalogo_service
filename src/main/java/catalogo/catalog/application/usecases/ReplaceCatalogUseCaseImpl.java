package catalogo.catalog.application.usecases;

import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.SyncType;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.in.ReplaceCatalogUseCase;
import catalogo.catalog.domain.ports.out.CatalogSyncStateRepository;
import catalogo.catalog.domain.ports.out.ProfessionalCategoryRepository;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import catalogo.catalog.domain.ports.out.WeeklyScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.function.Function;

// Reemplaza la copia local por un snapshot completo, con su versión y el resultado, en una sola transacción.
@Component
@RequiredArgsConstructor
public class ReplaceCatalogUseCaseImpl implements ReplaceCatalogUseCase {

    private final ProfessionalCategoryRepository professionalCategoryRepository;
    private final ProfessionalRepository professionalRepository;
    private final WeeklyScheduleRepository weeklyScheduleRepository;
    private final CatalogSyncStateRepository catalogSyncStateRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void replaceCatalog(long snapshotVersion, List<ProfessionalCategory> categories,
                               List<Professional> professionals, List<WeeklySchedule> weeklySchedules) {
        professionalCategoryRepository.saveAll(categories);
        professionalRepository.saveAll(professionals);
        weeklyScheduleRepository.saveAll(weeklySchedules);

        weeklyScheduleRepository.deleteAllExcept(idsOf(weeklySchedules, WeeklySchedule::getId));
        professionalRepository.deleteAllExcept(idsOf(professionals, Professional::getId));
        professionalCategoryRepository.deleteAllExcept(idsOf(categories, ProfessionalCategory::getId));

        CatalogSyncState state = catalogSyncStateRepository.get();
        state.recordSuccess(SyncType.FULL, snapshotVersion, clock.instant());
        catalogSyncStateRepository.save(state);
    }

    private static <T> List<Long> idsOf(List<T> items, Function<T, Long> id) {
        return items.stream().map(id).toList();
    }
}
