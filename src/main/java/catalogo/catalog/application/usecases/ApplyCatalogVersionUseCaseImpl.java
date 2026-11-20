package catalogo.catalog.application.usecases;

import catalogo.catalog.domain.model.ApplyCatalogVersionResult;
import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.SyncType;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.in.ApplyCatalogVersionUseCase;
import catalogo.catalog.domain.ports.out.CatalogSyncStateRepository;
import catalogo.catalog.domain.ports.out.ProfessionalCategoryRepository;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import catalogo.catalog.domain.ports.out.WeeklyScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Aplica una versión incremental. Resuelve las referencias, guarda lo que cambió, avanza la versión y registra el éxito.
@Component
@RequiredArgsConstructor
public class ApplyCatalogVersionUseCaseImpl implements ApplyCatalogVersionUseCase {

    private final ProfessionalCategoryRepository professionalCategoryRepository;
    private final ProfessionalRepository professionalRepository;
    private final WeeklyScheduleRepository weeklyScheduleRepository;
    private final CatalogSyncStateRepository catalogSyncStateRepository;
    private final Clock clock;

    @Override
    @Transactional
    public ApplyCatalogVersionResult applyCatalogVersion(long version, List<ProfessionalCategory> categories,
                                                         List<Professional> professionals,
                                                         List<WeeklySchedule> weeklySchedules) {
        List<String> problems = new ArrayList<>();

        Map<Long, ProfessionalCategory> knownCategories = withLocal(byId(categories, ProfessionalCategory::getId),
                referencedIds(professionals, Professional::getCategory, ProfessionalCategory::getId),
                professionalCategoryRepository::findAllByIds, ProfessionalCategory::getId);
        List<Professional> resolvedProfessionals = new ArrayList<>();
        for (Professional professional : professionals) {
            ProfessionalCategory category = known(professional.getCategory(), ProfessionalCategory::getId,
                    knownCategories);
            if (category == null) {
                problems.add("professional " + professional.getId() + " references the missing professional category "
                        + idOf(professional.getCategory(), ProfessionalCategory::getId));
            } else {
                resolvedProfessionals.add(withCategory(professional, category));
            }
        }

        Map<Long, Professional> knownProfessionals = withLocal(byId(resolvedProfessionals, Professional::getId),
                referencedIds(weeklySchedules, WeeklySchedule::getProfessional, Professional::getId),
                professionalRepository::findAllByIds, Professional::getId);
        List<WeeklySchedule> resolvedSchedules = new ArrayList<>();
        for (WeeklySchedule schedule : weeklySchedules) {
            Professional professional = known(schedule.getProfessional(), Professional::getId, knownProfessionals);
            if (professional == null) {
                problems.add("weekly schedule " + schedule.getId() + " references the missing professional "
                        + idOf(schedule.getProfessional(), Professional::getId));
            } else {
                resolvedSchedules.add(withProfessional(schedule, professional));
            }
        }

        if (!problems.isEmpty()) {
            return new ApplyCatalogVersionResult.NotVerifiable(problems);
        }

        professionalCategoryRepository.saveAll(categories);
        professionalRepository.saveAll(resolvedProfessionals);
        weeklyScheduleRepository.saveAll(resolvedSchedules);

        CatalogSyncState state = catalogSyncStateRepository.get();
        state.recordSuccess(SyncType.INCREMENTAL, version, clock.instant());
        catalogSyncStateRepository.save(state);
        return new ApplyCatalogVersionResult.Applied(version);
    }

    private static <T> Map<Long, T> withLocal(Map<Long, T> inVersion, Set<Long> referenced,
                                              Function<Collection<Long>, List<T>> findLocal, Function<T, Long> id) {
        Set<Long> missing = referenced.stream().filter(ref -> !inVersion.containsKey(ref)).collect(Collectors.toSet());
        Map<Long, T> known = new HashMap<>(inVersion);
        if (!missing.isEmpty()) {
            findLocal.apply(missing).forEach(item -> known.put(id.apply(item), item));
        }
        return known;
    }

    private static <T, R> Set<Long> referencedIds(List<T> items, Function<T, R> reference, Function<R, Long> id) {
        return items.stream()
                .map(reference)
                .filter(ref -> ref != null && id.apply(ref) != null)
                .map(id)
                .collect(Collectors.toSet());
    }

    private static <R> R known(R reference, Function<R, Long> id, Map<Long, R> known) {
        Long refId = idOf(reference, id);
        return refId == null ? null : known.get(refId);
    }

    private static <R> Long idOf(R reference, Function<R, Long> id) {
        return reference == null ? null : id.apply(reference);
    }

    private static <T> Map<Long, T> byId(List<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private static Professional withCategory(Professional professional, ProfessionalCategory category) {
        return Professional.builder()
                .id(professional.getId())
                .firstName(professional.getFirstName())
                .lastName(professional.getLastName())
                .enabled(professional.isEnabled())
                .category(category)
                .build();
    }

    private static WeeklySchedule withProfessional(WeeklySchedule schedule, Professional professional) {
        return WeeklySchedule.builder()
                .id(schedule.getId())
                .dayOfWeek(schedule.getDayOfWeek())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .slotDurationMinutes(schedule.getSlotDurationMinutes())
                .enabled(schedule.isEnabled())
                .professional(professional)
                .build();
    }
}
