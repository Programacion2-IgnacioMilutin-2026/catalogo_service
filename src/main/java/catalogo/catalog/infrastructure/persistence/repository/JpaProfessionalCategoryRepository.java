package catalogo.catalog.infrastructure.persistence.repository;

import catalogo.catalog.infrastructure.persistence.entity.ProfessionalCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

// Repositorio JPA de las categorías.
@Repository
public interface JpaProfessionalCategoryRepository extends JpaRepository<ProfessionalCategoryEntity, Long> {

    void deleteByIdNotIn(Collection<Long> ids);

    // La colación de la columna name da el orden en español.
    List<ProfessionalCategoryEntity> findAllByOrderByNameAscIdAsc();
}
