package catalogo.catalog.infrastructure.persistence.repository;

import catalogo.catalog.infrastructure.persistence.entity.CatalogSyncStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Repositorio JPA del estado de sincronización.
@Repository
public interface JpaCatalogSyncStateRepository extends JpaRepository<CatalogSyncStateEntity, Integer> {
}
