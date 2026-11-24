package catalogo.catalog.infrastructure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// La categoría dentro de un profesional: sin la descripción.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalCategorySummaryResponse {
    private Long id;
    private String name;
    private boolean enabled;
}
