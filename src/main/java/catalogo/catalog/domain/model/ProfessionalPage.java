package catalogo.catalog.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// Una página de la búsqueda de profesionales y el total de los que cumplen los filtros.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalPage {

    private List<ProfessionalWithSchedules> professionals;
    private long totalItems;
}
