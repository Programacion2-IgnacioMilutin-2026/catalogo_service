package catalogo.catalog.infrastructure.persistence.mapper;

import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.infrastructure.persistence.entity.ProfessionalCategoryEntity;
import org.springframework.stereotype.Component;

// Convierte una categoría entre el dominio y la entidad.
@Component
public class ProfessionalCategoryMapper {

    public ProfessionalCategoryEntity toEntity(ProfessionalCategory category) {
        if (category == null) {
            return null;
        }
        return ProfessionalCategoryEntity.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .enabled(category.isEnabled())
                .build();
    }

    public ProfessionalCategory toDomainModel(ProfessionalCategoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return ProfessionalCategory.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .enabled(entity.isEnabled())
                .build();
    }
}
