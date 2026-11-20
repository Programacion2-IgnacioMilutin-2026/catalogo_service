package catalogo.catedra.application.usecases;

import catalogo.catedra.TechnicalIntegrations;
import catalogo.catedra.application.support.CatalogRedisReader;
import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogMetadataResult;
import catalogo.catedra.domain.model.CatalogRedisPolicy;
import catalogo.catedra.domain.model.RedisReadResult;
import catalogo.catedra.domain.model.TechnicalSession;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// La lectura de la metadata traduce cada resultado de Redis al suyo.
class GetCatalogMetadataUseCaseImplTest {

    private static final TechnicalSession SESSION = TechnicalSession.enabled("technical-jwt",
            TechnicalIntegrations.complete(), Instant.parse("2026-11-18T12:00:00Z"));

    private final ScriptedRedisClient client = new ScriptedRedisClient();

    @Test
    void theMetadataIsObtained() {
        client.thenAnswerMetadata(new RedisReadResult.Received<>(new CatalogMetadata(59, 50)));

        assertThat(useCase().getCatalogMetadata())
                .isEqualTo(new CatalogMetadataResult.Obtained(new CatalogMetadata(59, 50)));
    }

    @Test
    void eachFailureIsTranslated() {
        client.thenAnswerMetadata(new RedisReadResult.Invalid<>("the catalog metadata does not exist"));
        assertThat(useCase().getCatalogMetadata())
                .isEqualTo(new CatalogMetadataResult.Invalid("the catalog metadata does not exist"));

        ScriptedRedisClient rejecting = new ScriptedRedisClient()
                .thenAnswerMetadata(new RedisReadResult.CredentialsRejected<>("Redis rejected the request: NOPERM"));
        assertThat(useCaseWith(rejecting).getCatalogMetadata()).isInstanceOf(CatalogMetadataResult.Rejected.class);

        ScriptedRedisClient unavailable = new ScriptedRedisClient()
                .thenAnswerMetadata(new RedisReadResult.Unavailable<>("Redis did not answer in time"));
        assertThat(useCaseWith(unavailable).getCatalogMetadata()).isInstanceOf(CatalogMetadataResult.Unavailable.class);
    }

    private GetCatalogMetadataUseCaseImpl useCase() {
        return useCaseWith(client);
    }

    private static GetCatalogMetadataUseCaseImpl useCaseWith(ScriptedRedisClient redisClient) {
        CatalogRedisReader reader = new CatalogRedisReader(() -> Optional.of(SESSION), rejected -> Optional.of(SESSION),
                new CatalogRedisPolicy(3, Duration.ofMillis(1), 2, Duration.ofMillis(5)));
        return new GetCatalogMetadataUseCaseImpl(reader, redisClient);
    }
}
