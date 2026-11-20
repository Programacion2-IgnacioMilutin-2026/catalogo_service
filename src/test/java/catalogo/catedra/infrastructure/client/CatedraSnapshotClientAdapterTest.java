package catalogo.catedra.infrastructure.client;

import catalogo.FakeHttpServer;
import catalogo.FakeHttpServer.Response;
import catalogo.catedra.CatalogSnapshots;
import catalogo.catedra.FakeCatedraServer;
import catalogo.catedra.domain.model.SnapshotFetchResult;
import catalogo.catedra.infrastructure.client.mapper.CatalogSnapshotMapper;
import catalogo.catedra.infrastructure.client.mapper.CatedraEntityMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// El pedido del snapshot contra la cátedra simulada.
class CatedraSnapshotClientAdapterTest {

    private static final String TOKEN = "test-technical-jwt";
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    private final FakeCatedraServer catedra = FakeCatedraServer.start();

    @AfterEach
    void stopCatedra() {
        catedra.close();
    }

    @Test
    void sendsTheTechnicalTokenAndReturnsTheSnapshot() {
        SnapshotFetchResult result = adapterFor(catedra.baseUrl()).fetchSnapshot(TOKEN);

        assertThat(result).isEqualTo(new SnapshotFetchResult.Received(CatalogSnapshots.valid()));
        assertThat(catedra.snapshotRequests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.header("Authorization")).isEqualTo("Bearer " + TOKEN);
            assertThat(request.header("Accept")).contains("application/json");
        });
    }

    @Test
    void aTrailingSlashInTheBaseUrlIsAccepted() {
        SnapshotFetchResult result = adapterFor(catedra.baseUrl() + "/").fetchSnapshot(TOKEN);

        assertThat(result).isInstanceOf(SnapshotFetchResult.Received.class);
    }

    @Test
    void aRejectedTokenCanBeRenewed() {
        catedra.respondToSnapshot(Response.empty(401));

        assertThat(adapterFor(catedra.baseUrl()).fetchSnapshot(TOKEN))
                .isEqualTo(new SnapshotFetchResult.TokenRejected());
    }

    @Test
    void aForbiddenRequestIsRejectedWithItsCode() {
        catedra.respondToSnapshot(FakeCatedraServer.problem(403, "STUDENT_INTEGRATION_NOT_FOUND"));

        assertThat(adapterFor(catedra.baseUrl()).fetchSnapshot(TOKEN)).isEqualTo(new SnapshotFetchResult.Rejected(
                "Central API rejected the snapshot request with HTTP 403 (STUDENT_INTEGRATION_NOT_FOUND)"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 429})
    void anotherClientErrorIsRejected(int status) {
        catedra.respondToSnapshot(Response.empty(status));

        assertThat(adapterFor(catedra.baseUrl()).fetchSnapshot(TOKEN))
                .isEqualTo(new SnapshotFetchResult.Rejected("Central API rejected the snapshot request with HTTP " + status));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void aServerErrorIsUnavailable(int status) {
        catedra.respondToSnapshot(Response.empty(status));

        assertThat(adapterFor(catedra.baseUrl()).fetchSnapshot(TOKEN))
                .isEqualTo(new SnapshotFetchResult.Unavailable("Central API answered HTTP " + status));
    }

    @Test
    void anAnswerSlowerThanTheReadTimeoutIsUnavailable() {
        catedra.respondToSnapshot(FakeCatedraServer.validSnapshot().after(READ_TIMEOUT.multipliedBy(3)));

        assertThat(adapterFor(catedra.baseUrl()).fetchSnapshot(TOKEN))
                .isInstanceOf(SnapshotFetchResult.Unavailable.class);
    }

    @Test
    void aClosedPortIsUnavailable() {
        FakeHttpServer closed = FakeHttpServer.start();
        String baseUrl = closed.baseUrl();
        closed.close();

        assertThat(adapterFor(baseUrl).fetchSnapshot(TOKEN)).isInstanceOf(SnapshotFetchResult.Unavailable.class);
    }

    @Test
    void anAnswerThatIsNotJsonIsInvalid() {
        catedra.respondToSnapshot(Response.of(200, "<html>not json</html>"));

        assertThat(adapterFor(catedra.baseUrl()).fetchSnapshot(TOKEN))
                .isEqualTo(new SnapshotFetchResult.Invalid("Central API answered an unreadable snapshot"));
    }

    @Test
    void anEmptyAnswerIsInvalid() {
        catedra.respondToSnapshot(Response.empty(200));

        assertThat(adapterFor(catedra.baseUrl()).fetchSnapshot(TOKEN))
                .isEqualTo(new SnapshotFetchResult.Invalid("Central API answered an incomplete snapshot: [empty response]"));
    }

    @Test
    void anAnswerWithMissingDataIsInvalidAndSaysWhatIsMissing() {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        ((ObjectNode) snapshot.get("professionals").get(1)).remove("lastName");
        catedra.respondToSnapshot(FakeCatedraServer.snapshotOf(snapshot));

        assertThat(adapterFor(catedra.baseUrl()).fetchSnapshot(TOKEN)).isEqualTo(new SnapshotFetchResult.Invalid(
                "Central API answered an incomplete snapshot: [professionals[1]: missing lastName]"));
    }

    private static CatedraSnapshotClientAdapter adapterFor(String baseUrl) {
        CatedraProperties properties = new CatedraProperties(baseUrl, FakeCatedraServer.TEST_USERNAME,
                FakeCatedraServer.TEST_PASSWORD, Duration.ofSeconds(2), Duration.ofSeconds(2),
                new CatedraProperties.Login(5, Duration.ofSeconds(1), 2, Duration.ofSeconds(30), Duration.ofSeconds(60)),
                new CatedraProperties.Snapshot(Duration.ofSeconds(2), READ_TIMEOUT, 5, Duration.ofSeconds(1), 2,
                        Duration.ofSeconds(30)),
                new CatedraProperties.Redis(Duration.ofSeconds(2), Duration.ofSeconds(2), 3, Duration.ofSeconds(1), 2,
                        Duration.ofSeconds(30)),
                new CatedraProperties.InitialLogin(Duration.ofMinutes(2), Duration.ofMillis(200)));
        return new CatedraSnapshotClientAdapter(properties, new CatalogSnapshotMapper(new CatedraEntityMapper()));
    }
}
