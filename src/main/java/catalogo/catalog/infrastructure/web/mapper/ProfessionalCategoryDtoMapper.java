package catalogo.catalog.infrastructure.web.mapper;

import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.infrastructure.web.dto.ProfessionalCategoryResponse;
import org.springframework.stereotype.Component;

// Convierte una categoría del dominio en la respuesta de la app.
@Component
public class ProfessionalCategoryDtoMapper {

    public ProfessionalCategoryResponse toResponse(ProfessionalCategory category) {
        if (category == null) {
            return null;
        }
        return ProfessionalCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .enabled(category.isEnabled())
                .build();
    }
}
