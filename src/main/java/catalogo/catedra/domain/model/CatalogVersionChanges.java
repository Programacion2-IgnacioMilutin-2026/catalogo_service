package catalogo.catedra.domain.model;

import java.util.List;

// Una versión del catálogo leída de Redis.
public record CatalogVersionChanges(
        long version,
        List<CatedraCategory> categories,
        List<CatedraProfessional> professionals,
        List<CatedraWeeklySchedule> weeklySchedules) {

    public CatalogVersionChanges {
        categories = List.copyOf(categories);
        professionals = List.copyOf(professionals);
        weeklySchedules = List.copyOf(weeklySchedules);
    }
}
