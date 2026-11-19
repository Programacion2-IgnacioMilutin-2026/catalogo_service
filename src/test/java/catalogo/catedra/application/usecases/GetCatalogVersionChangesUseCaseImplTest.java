package catalogo.catedra.application.usecases;

import catalogo.catedra.CatalogSnapshots;
import catalogo.catedra.TechnicalIntegrations;
import catalogo.catedra.application.support.CatalogRedisReader;
import catalogo.catedra.domain.model.CatalogRedisPolicy;
import catalogo.catedra.domain.model.CatalogVersionChanges;
import catalogo.catedra.domain.model.CatalogVersionChangesResult;
import catalogo.catedra.domain.model.RedisReadResult;
import catalogo.catedra.domain.model.TechnicalSession;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// La lectura de una versión pide esa versión y traduce cada resultado de Redis al suyo.
class GetCatalogVersionChangesUseCaseImplTest {

    private static final TechnicalSession SESSION = TechnicalSession.enabled("technical-jwt",
            TechnicalIntegrations.complete(), Instant.parse("2026-11-18T12:00:00Z"));
    private static final CatalogVersionChanges VERSION_59 = new CatalogVersionChanges(59, List.of(),
            List.of(CatalogSnapshots.professionals().getFirst()), List.of());

    @Test
    void theRequestedVersionIsReadAndObtained() {
        ScriptedRedisClient client = new ScriptedRedisClient().thenAnswerVersion(new RedisReadResult.Received<>(VERSION_59));

        assertThat(useCaseWith(client).getCatalogVersionChanges(59))
                .isEqualTo(new CatalogVersionChangesResult.Obtained(VERSION_59));
        assertThat(client.requestedVersions()).containsExactly(59L);
    }

    @Test
    void eachFailureIsTranslated() {
        assertThat(useCaseWith(new ScriptedRedisClient().thenAnswerVersion(
                new RedisReadResult.Invalid<>("the changes of version 59 do not exist"))).getCatalogVersionChanges(59))
                .isEqualTo(new CatalogVersionChangesResult.Invalid("the changes of version 59 do not exist"));
        assertThat(useCaseWith(new ScriptedRedisClient().thenAnswerVersion(
                new RedisReadResult.CredentialsRejected<>("Redis rejected the request: WRONGPASS")))
                .getCatalogVersionChanges(59)).isInstanceOf(CatalogVersionChangesResult.Rejected.class);
        assertThat(useCaseWith(new ScriptedRedisClient().thenAnswerVersion(
                new RedisReadResult.Unavailable<>("Redis did not answer in time"))).getCatalogVersionChanges(59))
                .isInstanceOf(CatalogVersionChangesResult.Unavailable.class);
    }

    private static GetCatalogVersionChangesUseCaseImpl useCaseWith(ScriptedRedisClient redisClient) {
        CatalogRedisReader reader = new CatalogRedisReader(() -> Optional.of(SESSION), rejected -> Optional.of(SESSION),
                new CatalogRedisPolicy(3, Duration.ofMillis(1), 2, Duration.ofMillis(5)));
        return new GetCatalogVersionChangesUseCaseImpl(reader, redisClient);
    }
}
