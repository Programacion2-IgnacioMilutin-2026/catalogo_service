package catalogo.catalog.infrastructure.persistence.repository;

import catalogo.catalog.infrastructure.persistence.entity.ProfessionalCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

// Repositorio JPA de las categorías.
@Repository
public interface JpaProfessionalCategoryRepository extends JpaRepository<ProfessionalCategoryEntity, Long> {

    void deleteByIdNotIn(Collection<Long> ids);
}
