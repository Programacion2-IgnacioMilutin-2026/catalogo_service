package catalogo.catedra.application.usecases;

import catalogo.catedra.CatalogSnapshots;
import catalogo.catedra.TechnicalIntegrations;
import catalogo.catedra.domain.model.CatalogSnapshot;
import catalogo.catedra.domain.model.CatalogSnapshotPolicy;
import catalogo.catedra.domain.model.CatalogSnapshotResult;
import catalogo.catedra.domain.model.SnapshotFetchResult;
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

// El pedido del snapshot con la sesión técnica, la renovación ante un 401 y los reintentos.
@ExtendWith(OutputCaptureExtension.class)
class GetCatalogSnapshotUseCaseImplTest {

    private static final String FIRST_TOKEN = "first-technical-jwt";
    private static final String RENEWED_TOKEN = "renewed-technical-jwt";
    private static final TechnicalSession FIRST = session(FIRST_TOKEN);
    private static final TechnicalSession RENEWED = session(RENEWED_TOKEN);
    private static final SnapshotFetchResult RECEIVED = new SnapshotFetchResult.Received(CatalogSnapshots.valid());
    private static final SnapshotFetchResult UNAVAILABLE = new SnapshotFetchResult.Unavailable("Central API answered HTTP 503");

    private final ScriptedSnapshotClient client = new ScriptedSnapshotClient();
    private final List<TechnicalSession> renewals = new ArrayList<>();
    private Optional<TechnicalSession> currentSession = Optional.of(FIRST);
    private Optional<TechnicalSession> renewedSession = Optional.of(RENEWED);
    private CatalogSnapshotPolicy policy = new CatalogSnapshotPolicy(5, Duration.ofMillis(1), 2, Duration.ofMillis(5));

    @Test
    void theSnapshotIsRequestedWithTheCurrentSession() {
        client.thenAnswer(RECEIVED);

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isEqualTo(new CatalogSnapshotResult.Obtained(CatalogSnapshots.valid()));
        assertThat(client.receivedTokens()).containsExactly(FIRST_TOKEN);
        assertThat(renewals).isEmpty();
    }

    @Test
    void aRejectedTokenIsRenewedOnceAndTheRequestIsRepeatedWithTheNewOne() {
        client.thenAnswer(new SnapshotFetchResult.TokenRejected()).thenAnswer(RECEIVED);

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isInstanceOf(CatalogSnapshotResult.Obtained.class);
        assertThat(client.receivedTokens()).containsExactly(FIRST_TOKEN, RENEWED_TOKEN);
        assertThat(renewals).containsExactly(FIRST);
    }

    @Test
    void aTokenRejectedAgainAfterRenewingIsRejectedWithoutAThirdRequest() {
        client.thenAnswer(new SnapshotFetchResult.TokenRejected());

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isEqualTo(
                new CatalogSnapshotResult.Rejected("Central API rejected the renewed technical token"));
        assertThat(client.calls()).isEqualTo(2);
        assertThat(renewals).hasSize(1);
    }

    @Test
    void aRenewalWithoutASessionIsUnavailableWithoutASecondRequest() {
        renewedSession = Optional.empty();
        client.thenAnswer(new SnapshotFetchResult.TokenRejected());

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isEqualTo(
                new CatalogSnapshotResult.Unavailable("The technical session could not be renewed"));
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void aTokenRejectedOnALaterAttemptIsRenewedTooAndOnlyOnce() {
        client.thenAnswer(UNAVAILABLE).thenAnswer(new SnapshotFetchResult.TokenRejected()).thenAnswer(RECEIVED);

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isInstanceOf(CatalogSnapshotResult.Obtained.class);
        assertThat(client.receivedTokens()).containsExactly(FIRST_TOKEN, FIRST_TOKEN, RENEWED_TOKEN);
        assertThat(renewals).hasSize(1);
    }

    @Test
    void withoutAnEnabledSessionARenewalIsRequestedFirst() {
        currentSession = Optional.empty();
        client.thenAnswer(RECEIVED);

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isInstanceOf(CatalogSnapshotResult.Obtained.class);
        assertThat(renewals).containsExactly((TechnicalSession) null);
        assertThat(client.receivedTokens()).containsExactly(RENEWED_TOKEN);
    }

    @Test
    void withoutAnySessionTheSnapshotIsNotRequested() {
        currentSession = Optional.empty();
        renewedSession = Optional.empty();

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isEqualTo(
                new CatalogSnapshotResult.Unavailable("The technical integration is not available"));
        assertThat(client.calls()).isZero();
    }

    @Test
    void anUnavailableCatedraIsRetriedUntilTheLimit() {
        client.thenAnswer(UNAVAILABLE);

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isEqualTo(new CatalogSnapshotResult.Unavailable(
                "Catalog snapshot not obtained after 5 attempts: Central API answered HTTP 503"));
        assertThat(client.calls()).isEqualTo(5);
    }

    @Test
    void aLaterAnswerAfterRetryingIsObtained() {
        client.thenAnswer(UNAVAILABLE).thenAnswer(UNAVAILABLE).thenAnswer(RECEIVED);

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isInstanceOf(CatalogSnapshotResult.Obtained.class);
        assertThat(client.calls()).isEqualTo(3);
    }

    @Test
    void theWaitGrowsBetweenRetries() {
        policy = new CatalogSnapshotPolicy(3, Duration.ofMillis(100), 2, Duration.ofSeconds(1));
        client.thenAnswer(UNAVAILABLE);
        long start = System.nanoTime();

        useCase().getCatalogSnapshot();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(300));
        assertThat(client.calls()).isEqualTo(3);
    }

    @Test
    void aRejectionIsNotRetried() {
        client.thenAnswer(new SnapshotFetchResult.Rejected("Central API rejected the snapshot request with HTTP 403"));

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isEqualTo(
                new CatalogSnapshotResult.Rejected("Central API rejected the snapshot request with HTTP 403"));
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void anInvalidAnswerIsNotRetried() {
        client.thenAnswer(new SnapshotFetchResult.Invalid("Central API answered an unreadable snapshot"));

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isEqualTo(new CatalogSnapshotResult.Invalid("Central API answered an unreadable snapshot"));
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void anInconsistentSnapshotIsInvalidAndNotRetried() {
        CatalogSnapshot empty = new CatalogSnapshot(58, List.of(), List.of(), List.of());
        client.thenAnswer(new SnapshotFetchResult.Received(empty));

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isEqualTo(
                new CatalogSnapshotResult.Invalid("Inconsistent snapshot: [no professional categories]"));
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void anUnexpectedErrorCountsAsUnavailableAndIsRetried() {
        client.thenThrow(new IllegalStateException("boom")).thenAnswer(RECEIVED);

        CatalogSnapshotResult result = useCase().getCatalogSnapshot();

        assertThat(result).isInstanceOf(CatalogSnapshotResult.Obtained.class);
        assertThat(client.calls()).isEqualTo(2);
    }

    @Test
    void theLogShowsEachAttemptAndTheResultWithoutTheTechnicalTokens(CapturedOutput output) {
        client.thenAnswer(UNAVAILABLE).thenAnswer(new SnapshotFetchResult.TokenRejected()).thenAnswer(RECEIVED);

        useCase().getCatalogSnapshot();

        assertThat(output.getOut())
                .contains("Catalog snapshot attempt 1/5 failed: Central API answered HTTP 503")
                .contains("repeating the request with the renewed session")
                .contains("Catalog snapshot 57 received: 2 categories, 3 professionals, 4 weekly schedules")
                .doesNotContain(FIRST_TOKEN)
                .doesNotContain(RENEWED_TOKEN);
    }

    private GetCatalogSnapshotUseCaseImpl useCase() {
        GetTechnicalSessionUseCase get = () -> currentSession;
        RenewTechnicalSessionUseCase renew = rejected -> {
            renewals.add(rejected);
            return renewedSession;
        };
        return new GetCatalogSnapshotUseCaseImpl(get, renew, client, policy);
    }

    private static TechnicalSession session(String token) {
        return TechnicalSession.enabled(token, TechnicalIntegrations.complete(), Instant.parse("2026-11-17T12:00:00Z"));
    }
}
