package catalogo.catalog.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

// Filtros de la búsqueda de profesionales.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalSearchCriteria {

    private Long categoryId;
    private Boolean enabled;
    private String name;
    private Set<DayOfWeek> daysOfWeek;
    private LocalTime from;
    private LocalTime to;

    public List<String> nameWords() {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        return Arrays.stream(name.strip().split("(?U)\\s+")).toList();
    }
}
