package catalogo.catalog.infrastructure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Una categoría tal como la recibe la app.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalCategoryResponse {
    private Long id;
    private String name;
    private String description;
    private boolean enabled;
}
