package catalogo.catedra.application.usecases;

import catalogo.MutableClock;
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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RenewTechnicalSessionUseCaseImplTest {

    private static final Instant START = Instant.parse("2026-11-15T12:00:00Z");
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(60);

    private final TechnicalLoginPolicy policy = new TechnicalLoginPolicy(
            5, Duration.ofMillis(1), 2, Duration.ofMillis(5), MIN_INTERVAL);
    private final MutableClock clock = new MutableClock(START);
    private final TechnicalSessionRepository repository = new InMemoryTechnicalSessionRepositoryAdapter();
    private final ScriptedLoginClient client = new ScriptedLoginClient();
    private final List<TechnicalIntegration> notified = new ArrayList<>();
    private final RenewTechnicalSessionUseCaseImpl useCase = new RenewTechnicalSessionUseCaseImpl(client, repository,
            policy, clock, notified::add);

    @Test
    void aRenewalThatEnablesTheSessionNotifiesItsIntegration() {
        repository.save(TechnicalSession.degraded(START));
        client.thenAnswer(ScriptedLoginClient.success("token-b"));
        clock.advance(MIN_INTERVAL);

        useCase.renewTechnicalSession(null);

        assertThat(notified).containsExactly(TechnicalIntegrations.complete());
    }

    @Test
    void anAlreadyEnabledSessionIsReturnedWithoutNotifying() {
        repository.save(enabled("token-a"));

        useCase.renewTechnicalSession(null);

        assertThat(notified).isEmpty();
    }

    @Test
    void aRenewalThatDoesNotEnableTheSessionNotifiesNobody() {
        repository.save(TechnicalSession.degraded(START));
        client.thenAnswer(new TechnicalLoginResult.Unavailable("HTTP 503"));
        clock.advance(MIN_INTERVAL);

        assertThat(useCase.renewTechnicalSession(null)).isEmpty();
        assertThat(notified).isEmpty();
    }

    @Test
    void aListenerThatFailsDoesNotChangeTheRenewal(CapturedOutput output) {
        RenewTechnicalSessionUseCaseImpl failing = new RenewTechnicalSessionUseCaseImpl(client, repository, policy,
                clock, integration -> {
                    throw new IllegalStateException("cannot start");
                });
        repository.save(TechnicalSession.degraded(START));
        client.thenAnswer(ScriptedLoginClient.success("token-b"));
        clock.advance(MIN_INTERVAL);

        Optional<TechnicalSession> renewed = failing.renewTechnicalSession(null);

        assertThat(renewed).map(TechnicalSession::token).contains("token-b");
        assertThat(output).contains("The enabled integration could not be notified: IllegalStateException");
    }

    @Test
    void aDegradedSessionIsNotRenewedBeforeTheMinimumIntervalAndIsRenewedAfterIt() {
        repository.save(TechnicalSession.degraded(START));
        client.thenAnswer(ScriptedLoginClient.success("token-b"));

        clock.advance(Duration.ofSeconds(59));
        assertThat(useCase.renewTechnicalSession(null)).isEmpty();
        assertThat(client.calls()).isZero();

        clock.advance(Duration.ofSeconds(1));
        Optional<TechnicalSession> renewed = useCase.renewTechnicalSession(null);

        assertThat(renewed).contains(TechnicalSession.enabled("token-b", TechnicalIntegrations.complete(), clock.instant()));
        assertThat(repository.get()).isEqualTo(renewed.orElseThrow());
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void aSessionWithAConfigurationErrorNeverLogsInAgain() {
        repository.save(TechnicalSession.configurationError(START));
        client.thenAnswer(ScriptedLoginClient.success());

        clock.advance(Duration.ofDays(2));
        for (int i = 0; i < 5; i++) {
            assertThat(useCase.renewTechnicalSession(null)).isEmpty();
        }

        assertThat(client.calls()).isZero();
        assertThat(repository.get()).isEqualTo(TechnicalSession.configurationError(START));
    }

    @Test
    void whileTheInitialLoginIsRunningARenewalDoesNotLogIn() {
        client.thenAnswer(ScriptedLoginClient.success());

        assertThat(useCase.renewTechnicalSession(null)).isEmpty();

        assertThat(client.calls()).isZero();
        assertThat(repository.get().state()).isEqualTo(IntegrationState.CONNECTING);
    }

    @Test
    void anEnabledSessionIsReturnedWithoutALoginWhenNoSessionWasRejected() {
        TechnicalSession current = enabled("token-a");
        repository.save(current);
        clock.advance(Duration.ofMinutes(10));

        assertThat(useCase.renewTechnicalSession(null)).contains(current);
        assertThat(client.calls()).isZero();
    }

    @Test
    void theRejectedCurrentSessionIsRenewedAfterTheMinimumInterval() {
        TechnicalSession rejected = enabled("token-a");
        repository.save(rejected);
        client.thenAnswer(ScriptedLoginClient.success("token-b"));
        clock.advance(MIN_INTERVAL);

        Optional<TechnicalSession> renewed = useCase.renewTechnicalSession(rejected);

        assertThat(renewed).map(TechnicalSession::token).contains("token-b");
        assertThat(repository.get()).isEqualTo(renewed.orElseThrow());
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void aRejectedSessionThatIsNoLongerTheCurrentOneGetsTheCurrentOneWithoutALogin() {
        TechnicalSession rejected = enabled("token-a");
        repository.save(rejected);
        client.thenAnswer(ScriptedLoginClient.success("token-b"));
        clock.advance(MIN_INTERVAL);
        TechnicalSession renewed = useCase.renewTechnicalSession(rejected).orElseThrow();

        clock.advance(Duration.ofMinutes(10));
        Optional<TechnicalSession> second = useCase.renewTechnicalSession(rejected);

        assertThat(second).contains(renewed);
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void aSessionRenewedLessThanTheMinimumIntervalAgoIsNotRenewedAgain() {
        TechnicalSession rejected = enabled("token-a");
        repository.save(rejected);
        client.thenAnswer(ScriptedLoginClient.success("token-b"));
        clock.advance(MIN_INTERVAL);
        TechnicalSession renewed = useCase.renewTechnicalSession(rejected).orElseThrow();

        clock.advance(Duration.ofSeconds(59));
        Optional<TechnicalSession> second = useCase.renewTechnicalSession(renewed);

        assertThat(second).isEmpty();
        assertThat(client.calls()).isEqualTo(1);
        assertThat(repository.get()).isEqualTo(renewed);
    }

    @Test
    @Timeout(20)
    void manySimultaneousRenewalsProduceASingleLoginAndEveryoneGetsTheNewSession() throws Exception {
        int requests = 20;
        TechnicalSession rejected = enabled("token-a");
        repository.save(rejected);
        clock.advance(MIN_INTERVAL);
        // El login no responde hasta que todos pidieron: así los pedidos se superponen seguro
        CountDownLatch everyoneAsked = new CountDownLatch(requests);
        client.beforeEachAnswer(() -> {
            await(everyoneAsked);
            sleep(Duration.ofMillis(150));
        }).thenAnswer(ScriptedLoginClient.success("token-b"));

        ExecutorService executor = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Optional<TechnicalSession>>> answers = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                answers.add(executor.submit(() -> {
                    everyoneAsked.countDown();
                    return useCase.renewTechnicalSession(rejected);
                }));
            }

            for (Future<Optional<TechnicalSession>> answer : answers) {
                assertThat(answer.get(10, TimeUnit.SECONDS)).map(TechnicalSession::token).contains("token-b");
            }
            assertThat(client.calls()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @EnumSource(value = ProvisioningStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PROVISIONED")
    void aRenewalThatIsNotProvisionedLeavesTheSessionDegraded(ProvisioningStatus status) {
        TechnicalSession rejected = enabled("token-a");
        repository.save(rejected);
        client.thenAnswer(new TechnicalLoginResult.Success("token-b", TechnicalIntegrations.withStatus(status)));
        clock.advance(MIN_INTERVAL);

        assertThat(useCase.renewTechnicalSession(rejected)).isEmpty();
        assertThat(repository.get()).isEqualTo(TechnicalSession.degraded(clock.instant()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"redisHost", "redisPort", "redisUsername", "redisPassword",
            "kafkaBootstrapServers", "kafkaConsumerGroupId", "kafkaCatalogTopic"})
    void aRenewalWithoutAConnectionValueLeavesTheSessionDegradedAndNamesTheValue(String field, CapturedOutput output) {
        TechnicalSession rejected = enabled("token-a");
        repository.save(rejected);
        client.thenAnswer(new TechnicalLoginResult.Success("token-b", TechnicalIntegrations.without(field)));
        clock.advance(MIN_INTERVAL);

        assertThat(useCase.renewTechnicalSession(rejected)).isEmpty();
        assertThat(repository.get()).isEqualTo(TechnicalSession.degraded(clock.instant()));
        assertThat(output).contains("incomplete integration").contains(field);
    }

    @Test
    void aRenewalRejectedByTheCatedraIsAConfigurationErrorAndStopsFurtherLogins() {
        TechnicalSession rejected = enabled("token-a");
        repository.save(rejected);
        client.thenAnswer(new TechnicalLoginResult.ConfigurationError("rejected with HTTP 401"));
        clock.advance(MIN_INTERVAL);

        assertThat(useCase.renewTechnicalSession(rejected)).isEmpty();
        assertThat(repository.get().state()).isEqualTo(IntegrationState.CONFIGURATION_ERROR);

        clock.advance(Duration.ofDays(1));
        assertThat(useCase.renewTechnicalSession(rejected)).isEmpty();
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void aRenewalWithTheCatedraUnavailableLeavesTheSessionDegradedUntilTheNextInterval() {
        TechnicalSession rejected = enabled("token-a");
        repository.save(rejected);
        client.thenAnswer(new TechnicalLoginResult.Unavailable("HTTP 503")).thenAnswer(ScriptedLoginClient.success("token-b"));
        clock.advance(MIN_INTERVAL);

        assertThat(useCase.renewTechnicalSession(rejected)).isEmpty();
        assertThat(repository.get()).isEqualTo(TechnicalSession.degraded(clock.instant()));

        clock.advance(Duration.ofSeconds(30));
        assertThat(useCase.renewTechnicalSession(rejected)).isEmpty();
        assertThat(client.calls()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(30));
        assertThat(useCase.renewTechnicalSession(rejected)).map(TechnicalSession::token).contains("token-b");
        assertThat(client.calls()).isEqualTo(2);
    }

    @Test
    void anUnexpectedErrorLeavesTheSessionDegradedNotStuck() {
        TechnicalSession rejected = enabled("token-a");
        repository.save(rejected);
        client.thenThrow(new IllegalStateException("unexpected"));
        clock.advance(MIN_INTERVAL);

        assertThat(useCase.renewTechnicalSession(rejected)).isEmpty();
        assertThat(repository.get()).isEqualTo(TechnicalSession.degraded(clock.instant()));
    }

    @Test
    void neitherTheTokenNorTheRedisPasswordReachesTheOutput(CapturedOutput output) {
        repository.save(TechnicalSession.degraded(START));
        client.thenAnswer(ScriptedLoginClient.success("token-b"));
        clock.advance(MIN_INTERVAL);

        System.out.println(useCase.renewTechnicalSession(null));

        assertThat(output)
                .contains("Technical login renewed: groupId=proyecto-de-prueba, provisioningStatus=PROVISIONED")
                .doesNotContain("token-b")
                .doesNotContain(TechnicalIntegrations.REDIS_PASSWORD);
    }

    private static TechnicalSession enabled(String token) {
        return TechnicalSession.enabled(token, TechnicalIntegrations.complete(), START);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
