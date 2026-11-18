package catalogo.catalog.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Entidad de persistencia de una categoría. El id es el de la cátedra.
@Getter
@Setter
@Entity
@Table(name = "professional_categories")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalCategoryEntity {
    @Id
    private Long id;
    private String name;
    private String description;
    private boolean enabled;
}
