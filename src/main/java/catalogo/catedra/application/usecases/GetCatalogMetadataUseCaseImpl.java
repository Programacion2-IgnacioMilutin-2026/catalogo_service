package catalogo.catedra.application.usecases;

import catalogo.catedra.application.support.CatalogRedisReader;
import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogMetadataResult;
import catalogo.catedra.domain.model.RedisReadResult;
import catalogo.catedra.domain.ports.in.GetCatalogMetadataUseCase;
import catalogo.catedra.domain.ports.out.CatalogRedisClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Lee la metadata del catálogo en Redis.
@Component
@RequiredArgsConstructor
public class GetCatalogMetadataUseCaseImpl implements GetCatalogMetadataUseCase {

    private final CatalogRedisReader catalogRedisReader;
    private final CatalogRedisClient catalogRedisClient;

    @Override
    public CatalogMetadataResult getCatalogMetadata() {
        return switch (catalogRedisReader.read("metadata", catalogRedisClient::readMetadata)) {
            case RedisReadResult.Received<CatalogMetadata> received -> new CatalogMetadataResult.Obtained(received.value());
            case RedisReadResult.Unavailable<CatalogMetadata> unavailable ->
                    new CatalogMetadataResult.Unavailable(unavailable.reason());
            case RedisReadResult.CredentialsRejected<CatalogMetadata> rejected ->
                    new CatalogMetadataResult.Rejected(rejected.reason());
            case RedisReadResult.Invalid<CatalogMetadata> invalid -> new CatalogMetadataResult.Invalid(invalid.reason());
        };
    }
}
