package catalogo.catedra.application.usecases;

import catalogo.catedra.application.support.CatalogRedisReader;
import catalogo.catedra.domain.model.CatalogVersionChanges;
import catalogo.catedra.domain.model.CatalogVersionChangesResult;
import catalogo.catedra.domain.model.RedisReadResult;
import catalogo.catedra.domain.ports.in.GetCatalogVersionChangesUseCase;
import catalogo.catedra.domain.ports.out.CatalogRedisClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Lee en Redis una versión del catálogo con el estado vigente de cada entidad que cambió.
@Component
@RequiredArgsConstructor
public class GetCatalogVersionChangesUseCaseImpl implements GetCatalogVersionChangesUseCase {

    private final CatalogRedisReader catalogRedisReader;
    private final CatalogRedisClient catalogRedisClient;

    @Override
    public CatalogVersionChangesResult getCatalogVersionChanges(long version) {
        RedisReadResult<CatalogVersionChanges> result = catalogRedisReader.read("version " + version,
                integration -> catalogRedisClient.readVersionChanges(integration, version));
        return switch (result) {
            case RedisReadResult.Received<CatalogVersionChanges> received ->
                    new CatalogVersionChangesResult.Obtained(received.value());
            case RedisReadResult.Unavailable<CatalogVersionChanges> unavailable ->
                    new CatalogVersionChangesResult.Unavailable(unavailable.reason());
            case RedisReadResult.CredentialsRejected<CatalogVersionChanges> rejected ->
                    new CatalogVersionChangesResult.Rejected(rejected.reason());
            case RedisReadResult.Invalid<CatalogVersionChanges> invalid ->
                    new CatalogVersionChangesResult.Invalid(invalid.reason());
        };
    }
}
