package catalogo.catalog.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// Un profesional con sus horarios semanales.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalWithSchedules {

    private Professional professional;
    private List<WeeklySchedule> weeklySchedules;
}
