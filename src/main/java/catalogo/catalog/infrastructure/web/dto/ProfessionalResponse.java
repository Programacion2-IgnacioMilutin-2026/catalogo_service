package catalogo.catalog.infrastructure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// Un profesional con su categoría y sus horarios semanales.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private ProfessionalCategorySummaryResponse category;
    private List<WeeklyScheduleResponse> weeklySchedules;
}
