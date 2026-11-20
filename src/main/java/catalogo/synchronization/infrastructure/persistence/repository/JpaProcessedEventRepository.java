package catalogo.synchronization.infrastructure.persistence.repository;

import catalogo.synchronization.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Acceso JPA a los avisos procesados.
@Repository
public interface JpaProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {
}
