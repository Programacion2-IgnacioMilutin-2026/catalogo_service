package catalogo.catalog.infrastructure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// Una página de la búsqueda de profesionales tal como la recibe la app.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalPageResponse {
    private List<ProfessionalResponse> items;
    private int page;
    private int size;
    private long totalItems;
}
