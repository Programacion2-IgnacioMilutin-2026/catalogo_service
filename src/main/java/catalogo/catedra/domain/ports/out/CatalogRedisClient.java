package catalogo.catedra.domain.ports.out;

import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogVersionChanges;
import catalogo.catedra.domain.model.RedisReadResult;
import catalogo.catedra.domain.model.TechnicalIntegration;

// Lecturas de Redis de la cátedra.
public interface CatalogRedisClient {

    RedisReadResult<CatalogMetadata> readMetadata(TechnicalIntegration integration);

    RedisReadResult<CatalogVersionChanges> readVersionChanges(TechnicalIntegration integration, long version);
}
