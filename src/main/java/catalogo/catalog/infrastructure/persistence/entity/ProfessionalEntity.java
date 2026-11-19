package catalogo.catalog.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Entidad de persistencia de un profesional. El id es el de la cátedra.
@Getter
@Setter
@Entity
@Table(name = "professionals")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalEntity {
    @Id
    private Long id;
    private String firstName;
    private String lastName;
    private boolean enabled;
    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id")
    private ProfessionalCategoryEntity category;
}
