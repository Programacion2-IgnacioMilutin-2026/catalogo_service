package catalogo.catedra.domain.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;

// Snapshot completo del catálogo, ya leído. Prueba la consistencia para reemplazar la copia local.
public record CatalogSnapshot(long snapshotVersion,
                              List<SnapshotCategory> categories,
                              List<SnapshotProfessional> professionals,
                              List<SnapshotWeeklySchedule> weeklySchedules) {

    public CatalogSnapshot {
        categories = List.copyOf(categories);
        professionals = List.copyOf(professionals);
        weeklySchedules = List.copyOf(weeklySchedules);
    }

    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (categories.isEmpty()) {
            problems.add("no professional categories");
        }
        Set<Long> categoryIds = idsOf(categories, SnapshotCategory::id, "professional category", problems);
        Set<Long> professionalIds = idsOf(professionals, SnapshotProfessional::id, "professional", problems);
        idsOf(weeklySchedules, SnapshotWeeklySchedule::id, "weekly schedule", problems);
        professionals.stream()
                .filter(professional -> !categoryIds.contains(professional.categoryId()))
                .forEach(professional -> problems.add("professional " + professional.id()
                        + " references the missing professional category " + professional.categoryId()));
        weeklySchedules.stream()
                .filter(schedule -> !professionalIds.contains(schedule.professionalId()))
                .forEach(schedule -> problems.add("weekly schedule " + schedule.id()
                        + " references the missing professional " + schedule.professionalId()));
        return List.copyOf(problems);
    }

    private static <T> Set<Long> idsOf(List<T> items, ToLongFunction<T> id, String name, List<String> problems) {
        Set<Long> ids = new HashSet<>();
        for (T item : items) {
            long value = id.applyAsLong(item);
            if (!ids.add(value)) {
                problems.add("repeated " + name + " id " + value);
            }
        }
        return ids;
    }
}
