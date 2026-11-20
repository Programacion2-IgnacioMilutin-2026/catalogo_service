package catalogo.catedra.application.usecases;

import catalogo.catedra.TechnicalIntegrations;
import catalogo.catedra.domain.model.IntegrationState;
import catalogo.catedra.domain.model.ProvisioningStatus;
import catalogo.catedra.domain.model.TechnicalIntegration;
import catalogo.catedra.domain.model.TechnicalLoginPolicy;
import catalogo.catedra.domain.model.TechnicalLoginResult;
import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.out.TechnicalSessionRepository;
import catalogo.catedra.infrastructure.persistence.adapter.InMemoryTechnicalSessionRepositoryAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class StartTechnicalSessionUseCaseImplTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    private final TechnicalLoginPolicy policy = new TechnicalLoginPolicy(
            MAX_ATTEMPTS, Duration.ofMillis(1), 2, Duration.ofMillis(5), Duration.ofSeconds(60));
    private final TechnicalSessionRepository repository = new InMemoryTechnicalSessionRepositoryAdapter();
    private final ScriptedLoginClient client = new ScriptedLoginClient();
    private final List<TechnicalIntegration> notified = new ArrayList<>();
    private final StartTechnicalSessionUseCaseImpl useCase = new StartTechnicalSessionUseCaseImpl(
            client, repository, policy, Clock.fixed(NOW, ZoneOffset.UTC), notified::add);

    @Test
    void aProvisionedAndCompleteLoginEnablesTheSessionAtTheFirstAttempt() {
        client.thenAnswer(ScriptedLoginClient.success());

        TechnicalSession session = useCase.startTechnicalSession();

        assertThat(session).isEqualTo(
                TechnicalSession.enabled(ScriptedLoginClient.TOKEN, TechnicalIntegrations.complete(), NOW));
        assertThat(repository.get()).isEqualTo(session);
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void anEnabledSessionNotifiesItsIntegrationOnce() {
        client.thenAnswer(new TechnicalLoginResult.Unavailable("HTTP 503")).thenAnswer(ScriptedLoginClient.success());

        useCase.startTechnicalSession();

        assertThat(notified).containsExactly(TechnicalIntegrations.complete());
    }

    @Test
    void aLoginThatDoesNotEnableTheSessionNotifiesNobody() {
        for (TechnicalLoginResult result : List.of(
                new TechnicalLoginResult.ConfigurationError("rejected with HTTP 401"),
                new TechnicalLoginResult.Unavailable("HTTP 503"),
                new TechnicalLoginResult.Success(ScriptedLoginClient.TOKEN,
                        TechnicalIntegrations.withStatus(ProvisioningStatus.PENDING)),
                new TechnicalLoginResult.Success(ScriptedLoginClient.TOKEN,
                        TechnicalIntegrations.without("kafkaCatalogTopic")))) {
            TechnicalSession session = new StartTechnicalSessionUseCaseImpl(new ScriptedLoginClient().thenAnswer(result),
                    repository, policy, Clock.fixed(NOW, ZoneOffset.UTC), notified::add).startTechnicalSession();
            assertThat(session.isEnabled()).isFalse();
        }

        assertThat(notified).isEmpty();
    }

    @Test
    void aListenerThatFailsDoesNotChangeTheLogin(CapturedOutput output) {
        StartTechnicalSessionUseCaseImpl failing = new StartTechnicalSessionUseCaseImpl(
                client, repository, policy, Clock.fixed(NOW, ZoneOffset.UTC), integration -> {
                    throw new IllegalStateException("cannot start");
                });
        client.thenAnswer(ScriptedLoginClient.success());

        TechnicalSession session = failing.startTechnicalSession();

        assertThat(session.isEnabled()).isTrue();
        assertThat(repository.get()).isEqualTo(session);
        assertThat(output).contains("The enabled integration could not be notified: IllegalStateException");
    }

    @ParameterizedTest
    @EnumSource(value = ProvisioningStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PROVISIONED")
    void anIntegrationThatIsNotProvisionedIsRetriedUntilTheLimitAndLeavesTheSessionDegraded(ProvisioningStatus status) {
        client.thenAnswer(new TechnicalLoginResult.Success(ScriptedLoginClient.TOKEN, TechnicalIntegrations.withStatus(status)));

        TechnicalSession session = useCase.startTechnicalSession();

        assertThat(session).isEqualTo(TechnicalSession.degraded(NOW));
        assertThat(repository.get()).isEqualTo(session);
        assertThat(client.calls()).isEqualTo(MAX_ATTEMPTS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"redisHost", "redisPort", "redisUsername", "redisPassword",
            "kafkaBootstrapServers", "kafkaConsumerGroupId", "kafkaCatalogTopic"})
    void aProvisionedLoginWithoutAConnectionValueIsRetriedUntilTheLimitAndTheValueIsNamed(String field, CapturedOutput output) {
        client.thenAnswer(new TechnicalLoginResult.Success(ScriptedLoginClient.TOKEN, TechnicalIntegrations.without(field)));

        TechnicalSession session = useCase.startTechnicalSession();

        assertThat(session).isEqualTo(TechnicalSession.degraded(NOW));
        assertThat(client.calls()).isEqualTo(MAX_ATTEMPTS);
        assertThat(output).contains("incomplete integration").contains(field);
    }

    @Test
    void anIncompleteLoginFollowedByACompleteOneEnablesTheSession() {
        client.thenAnswer(new TechnicalLoginResult.Success(ScriptedLoginClient.TOKEN, TechnicalIntegrations.without("redisHost")))
                .thenAnswer(ScriptedLoginClient.success());

        TechnicalSession session = useCase.startTechnicalSession();

        assertThat(session.state()).isEqualTo(IntegrationState.ENABLED);
        assertThat(client.calls()).isEqualTo(2);
    }

    @Test
    void aLoginWithoutTheGroupIdStillEnablesTheSession() {
        client.thenAnswer(new TechnicalLoginResult.Success(ScriptedLoginClient.TOKEN, TechnicalIntegrations.without("groupId")));

        assertThat(useCase.startTechnicalSession().state()).isEqualTo(IntegrationState.ENABLED);
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void aConfigurationErrorStopsAtTheFirstAttempt(CapturedOutput output) {
        client.thenAnswer(new TechnicalLoginResult.ConfigurationError("rejected with HTTP 401"));

        TechnicalSession session = useCase.startTechnicalSession();

        assertThat(session).isEqualTo(TechnicalSession.configurationError(NOW));
        assertThat(repository.get()).isEqualTo(session);
        assertThat(client.calls()).isEqualTo(1);
        assertThat(output).contains("configuration error").contains("rejected with HTTP 401");
    }

    @Test
    void anUnavailableCatedraIsRetriedAndALaterSuccessEnablesTheSession(CapturedOutput output) {
        client.thenAnswer(new TechnicalLoginResult.Unavailable("HTTP 503"))
                .thenAnswer(new TechnicalLoginResult.Unavailable("timeout"))
                .thenAnswer(ScriptedLoginClient.success());

        TechnicalSession session = useCase.startTechnicalSession();

        assertThat(session.state()).isEqualTo(IntegrationState.ENABLED);
        assertThat(client.calls()).isEqualTo(3);
        assertThat(output).contains("attempt 1/5 failed: HTTP 503").contains("attempt 2/5 failed: timeout");
    }

    @Test
    void aCatedraThatStaysUnavailableLeavesTheSessionDegradedAfterTheLimit() {
        client.thenAnswer(new TechnicalLoginResult.Unavailable("HTTP 500"));

        TechnicalSession session = useCase.startTechnicalSession();

        assertThat(session).isEqualTo(TechnicalSession.degraded(NOW));
        assertThat(client.calls()).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    void anUnexpectedErrorCountsAsAFailedAttempt() {
        client.thenThrow(new IllegalStateException("unexpected")).thenAnswer(ScriptedLoginClient.success());

        TechnicalSession session = useCase.startTechnicalSession();

        assertThat(session.state()).isEqualTo(IntegrationState.ENABLED);
        assertThat(client.calls()).isEqualTo(2);
    }

    @Test
    void aClientThatAlwaysFailsUnexpectedlyLeavesTheSessionDegradedNotConnecting() {
        client.thenThrow(new IllegalStateException("unexpected"));

        TechnicalSession session = useCase.startTechnicalSession();

        assertThat(session.state()).isEqualTo(IntegrationState.DEGRADED);
        assertThat(repository.get().state()).isEqualTo(IntegrationState.DEGRADED);
        assertThat(client.calls()).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    void theSessionIsConnectingWhileTheLoginIsRunning() {
        repository.save(TechnicalSession.degraded(NOW));
        AtomicReference<IntegrationState> stateDuringLogin = new AtomicReference<>();
        client.beforeEachAnswer(() -> stateDuringLogin.set(repository.get().state()))
                .thenAnswer(ScriptedLoginClient.success());

        useCase.startTechnicalSession();

        assertThat(stateDuringLogin.get()).isEqualTo(IntegrationState.CONNECTING);
    }

    @Test
    void anInterruptedWaitStopsTheRetriesAndLeavesTheSessionDegraded() {
        client.thenAnswer(new TechnicalLoginResult.Unavailable("HTTP 503"));
        Thread.currentThread().interrupt();
        try {
            TechnicalSession session = useCase.startTechnicalSession();

            assertThat(session.state()).isEqualTo(IntegrationState.DEGRADED);
            assertThat(client.calls()).isEqualTo(1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void neitherTheTokenNorTheRedisPasswordReachesTheOutput(CapturedOutput output) {
        client.thenAnswer(ScriptedLoginClient.success());

        System.out.println(useCase.startTechnicalSession());

        assertThat(output)
                .contains("Technical login succeeded: groupId=proyecto-de-prueba, provisioningStatus=PROVISIONED")
                .doesNotContain(ScriptedLoginClient.TOKEN)
                .doesNotContain(TechnicalIntegrations.REDIS_PASSWORD);
    }
}
