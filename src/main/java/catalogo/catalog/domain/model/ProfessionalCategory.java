package catalogo.catalog.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Categoría de profesionales de la copia local.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalCategory {
    private Long id;
    private String name;
    private String description;
    private boolean enabled;
}
