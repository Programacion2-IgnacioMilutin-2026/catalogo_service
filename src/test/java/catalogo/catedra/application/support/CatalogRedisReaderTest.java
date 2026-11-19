package catalogo.catedra.application.support;

import catalogo.catedra.TechnicalIntegrations;
import catalogo.catedra.application.usecases.ScriptedRedisClient;
import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogRedisPolicy;
import catalogo.catedra.domain.model.RedisReadResult;
import catalogo.catedra.domain.model.TechnicalIntegration;
import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.in.GetTechnicalSessionUseCase;
import catalogo.catedra.domain.ports.in.RenewTechnicalSessionUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Una lectura de Redis con la sesión técnica, la renovación ante un rechazo y los reintentos.
@ExtendWith(OutputCaptureExtension.class)
class CatalogRedisReaderTest {

    private static final TechnicalIntegration FIRST_INTEGRATION = TechnicalIntegrations.complete();
    private static final TechnicalIntegration RENEWED_INTEGRATION = withRedisUsername("grp_renewed");
    private static final TechnicalSession FIRST = session(FIRST_INTEGRATION);
    private static final TechnicalSession RENEWED = session(RENEWED_INTEGRATION);
    private static final RedisReadResult<CatalogMetadata> RECEIVED =
            new RedisReadResult.Received<>(new CatalogMetadata(59, 50));
    private static final RedisReadResult<CatalogMetadata> UNAVAILABLE =
            new RedisReadResult.Unavailable<>("Redis did not answer in time");
    private static final RedisReadResult<CatalogMetadata> REJECTED =
            new RedisReadResult.CredentialsRejected<>("Redis rejected the request: WRONGPASS");

    private final ScriptedRedisClient client = new ScriptedRedisClient();
    private final List<TechnicalSession> renewals = new ArrayList<>();
    private Optional<TechnicalSession> currentSession = Optional.of(FIRST);
    private Optional<TechnicalSession> renewedSession = Optional.of(RENEWED);
    private CatalogRedisPolicy policy = new CatalogRedisPolicy(3, Duration.ofMillis(1), 2, Duration.ofMillis(5));

    @Test
    void itReadsWithTheIntegrationOfTheCurrentSession() {
        client.thenAnswerMetadata(RECEIVED);

        assertThat(read()).isEqualTo(RECEIVED);
        assertThat(client.receivedIntegrations()).containsExactly(FIRST_INTEGRATION);
        assertThat(renewals).isEmpty();
    }

    @Test
    void withoutASessionItAsksForOneAndReadsWithIt() {
        currentSession = Optional.empty();
        client.thenAnswerMetadata(RECEIVED);

        assertThat(read()).isEqualTo(RECEIVED);
        assertThat(renewals).containsExactly((TechnicalSession) null);
        assertThat(client.receivedIntegrations()).containsExactly(RENEWED_INTEGRATION);
    }

    @Test
    void withoutASessionAndWithoutRenewalNothingIsRead() {
        currentSession = Optional.empty();
        renewedSession = Optional.empty();

        assertThat(read()).isEqualTo(new RedisReadResult.Unavailable<>("The technical integration is not available"));
        assertThat(client.receivedIntegrations()).isEmpty();
    }

    @Test
    void rejectedCredentialsAreRenewedOnceAndTheReadIsRepeatedWithTheNewIntegration() {
        client.thenAnswerMetadata(REJECTED).thenAnswerMetadata(RECEIVED);

        assertThat(read()).isEqualTo(RECEIVED);
        assertThat(client.receivedIntegrations()).containsExactly(FIRST_INTEGRATION, RENEWED_INTEGRATION);
        assertThat(renewals).containsExactly(FIRST);
    }

    @Test
    void credentialsRejectedAgainAfterRenewingAreRejectedWithoutAThirdRead() {
        client.thenAnswerMetadata(REJECTED);

        RedisReadResult<CatalogMetadata> result = read();

        assertThat(result).isInstanceOf(RedisReadResult.CredentialsRejected.class);
        assertThat(client.receivedIntegrations()).hasSize(2);
        assertThat(renewals).hasSize(1);
    }

    @Test
    void aRenewalWithoutASessionIsUnavailableWithoutASecondRead() {
        renewedSession = Optional.empty();
        client.thenAnswerMetadata(REJECTED);

        assertThat(read()).isEqualTo(new RedisReadResult.Unavailable<>("The technical session could not be renewed"));
        assertThat(client.receivedIntegrations()).hasSize(1);
    }

    @Test
    void anUnavailableRedisIsRetriedAndALaterAnswerIsUsed(CapturedOutput output) {
        client.thenAnswerMetadata(UNAVAILABLE).thenAnswerMetadata(UNAVAILABLE).thenAnswerMetadata(RECEIVED);

        assertThat(read()).isEqualTo(RECEIVED);
        assertThat(client.receivedIntegrations()).hasSize(3);
        assertThat(output).contains("Catalog Redis metadata attempt 1/3 failed: Redis did not answer in time",
                "Catalog Redis metadata attempt 2/3 failed");
    }

    @Test
    void anUnavailableRedisIsUnavailableAfterTheLastAttempt() {
        client.thenAnswerMetadata(UNAVAILABLE);

        RedisReadResult<CatalogMetadata> result = read();

        assertThat(result).isEqualTo(new RedisReadResult.Unavailable<>(
                "Redis metadata not read after 3 attempts: Redis did not answer in time"));
        assertThat(client.receivedIntegrations()).hasSize(3);
    }

    @Test
    void theWaitsBetweenAttemptsGrow() {
        policy = new CatalogRedisPolicy(3, Duration.ofMillis(100), 2, Duration.ofSeconds(1));
        client.thenAnswerMetadata(UNAVAILABLE);
        long start = System.nanoTime();

        read();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(300));
    }

    @Test
    void invalidDataIsReturnedWithoutRetrying() {
        RedisReadResult<CatalogMetadata> invalid = new RedisReadResult.Invalid<>("the catalog metadata does not exist");
        client.thenAnswerMetadata(invalid);

        assertThat(read()).isEqualTo(invalid);
        assertThat(client.receivedIntegrations()).hasSize(1);
    }

    @Test
    void aRejectionOnALaterAttemptIsRenewedTooAndOnlyOnce() {
        client.thenAnswerMetadata(UNAVAILABLE).thenAnswerMetadata(REJECTED).thenAnswerMetadata(RECEIVED);

        assertThat(read()).isEqualTo(RECEIVED);
        assertThat(client.receivedIntegrations()).containsExactly(FIRST_INTEGRATION, FIRST_INTEGRATION,
                RENEWED_INTEGRATION);
        assertThat(renewals).hasSize(1);
    }

    @Test
    void anUnexpectedErrorIsUnavailableAndRetried() {
        int[] calls = {0};
        RedisReadResult<CatalogMetadata> result = reader().read("metadata", integration -> {
            calls[0]++;
            if (calls[0] == 1) {
                throw new IllegalStateException("boom");
            }
            return RECEIVED;
        });

        assertThat(result).isEqualTo(RECEIVED);
        assertThat(calls[0]).isEqualTo(2);
    }

    @Test
    void theLogDoesNotShowTheRedisPassword(CapturedOutput output) {
        client.thenAnswerMetadata(UNAVAILABLE).thenAnswerMetadata(REJECTED);

        read();

        assertThat(output).doesNotContain(TechnicalIntegrations.REDIS_PASSWORD);
    }

    private RedisReadResult<CatalogMetadata> read() {
        return reader().read("metadata", client::readMetadata);
    }

    private CatalogRedisReader reader() {
        GetTechnicalSessionUseCase get = () -> currentSession;
        RenewTechnicalSessionUseCase renew = rejected -> {
            renewals.add(rejected);
            return renewedSession;
        };
        return new CatalogRedisReader(get, renew, policy);
    }

    private static TechnicalSession session(TechnicalIntegration integration) {
        return TechnicalSession.enabled("technical-jwt", integration, Instant.parse("2026-11-18T12:00:00Z"));
    }

    private static TechnicalIntegration withRedisUsername(String username) {
        TechnicalIntegration complete = TechnicalIntegrations.complete();
        return new TechnicalIntegration(complete.groupId(), complete.redisHost(), complete.redisPort(), username,
                complete.redisPassword(), complete.kafkaBootstrapServers(), complete.kafkaConsumerGroupId(),
                complete.kafkaCatalogTopic(), complete.provisioningStatus());
    }
}
