package catalogo.catalog.infrastructure.persistence.mapper;

import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.infrastructure.persistence.entity.ProfessionalEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Convierte un profesional entre el dominio y la entidad.
@Component
@RequiredArgsConstructor
public class ProfessionalMapper {

    private final ProfessionalCategoryMapper professionalCategoryMapper;

    public ProfessionalEntity toEntity(Professional professional) {
        if (professional == null) {
            return null;
        }
        return ProfessionalEntity.builder()
                .id(professional.getId())
                .firstName(professional.getFirstName())
                .lastName(professional.getLastName())
                .enabled(professional.isEnabled())
                .category(professionalCategoryMapper.toEntity(professional.getCategory()))
                .build();
    }

    public Professional toDomainModel(ProfessionalEntity entity) {
        if (entity == null) {
            return null;
        }
        return Professional.builder()
                .id(entity.getId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .enabled(entity.isEnabled())
                .category(professionalCategoryMapper.toDomainModel(entity.getCategory()))
                .build();
    }
}
