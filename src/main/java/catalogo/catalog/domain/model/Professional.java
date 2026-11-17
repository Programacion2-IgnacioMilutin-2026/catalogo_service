package catalogo.catalog.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Profesional de la copia local.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Professional {
    private Long id;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private ProfessionalCategory category;
}
