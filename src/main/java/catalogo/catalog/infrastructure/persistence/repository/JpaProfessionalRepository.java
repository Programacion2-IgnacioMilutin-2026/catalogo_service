package catalogo.catalog.infrastructure.persistence.repository;

import catalogo.catalog.infrastructure.persistence.entity.ProfessionalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

// Repositorio JPA de los profesionales.
@Repository
public interface JpaProfessionalRepository extends JpaRepository<ProfessionalEntity, Long> {

    void deleteByIdNotIn(Collection<Long> ids);
}
