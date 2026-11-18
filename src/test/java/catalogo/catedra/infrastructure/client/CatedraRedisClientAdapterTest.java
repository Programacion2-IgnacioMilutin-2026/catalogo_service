package catalogo.catedra.infrastructure.client;

import catalogo.catedra.CatalogSnapshots;
import catalogo.catedra.CatedraRedis;
import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogVersionChanges;
import catalogo.catedra.domain.model.RedisReadResult;
import catalogo.catedra.domain.model.TechnicalIntegration;
import catalogo.catedra.infrastructure.client.mapper.CatalogVersionChangesMapper;
import catalogo.catedra.infrastructure.client.mapper.CatedraEntityMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Las lecturas de Redis contra un Redis real con el mismo ACL que la cátedra.
@ExtendWith(OutputCaptureExtension.class)
class CatedraRedisClientAdapterTest {

    private static final Duration COMMAND_TIMEOUT = Duration.ofMillis(500);

    private static CatedraRedis redis;

    private CatedraRedisClientAdapter adapter;

    @BeforeAll
    static void startRedis() {
        redis = CatedraRedis.start();
    }

    @AfterAll
    static void stopRedis() {
        redis.close();
    }

    @BeforeEach
    void catalogInRedis() {
        redis.clear();
        redis.putMetadata(59, 50);
        redis.putChanges(59, List.of(502L), List.of(601L), List.of(702L));
        redis.putSnapshotEntities();
        adapter = new CatedraRedisClientAdapter(properties(), new CatalogVersionChangesMapper(new CatedraEntityMapper()));
    }

    @AfterEach
    void closeAdapter() {
        adapter.destroy();
    }

    @Test
    void itConnectsWithTheCatedraAclWithoutPingAndReadsTheMetadata() {
        assertThat(adapter.readMetadata(redis.integration()))
                .isEqualTo(new RedisReadResult.Received<>(new CatalogMetadata(59, 50)));
    }

    // Lettuce manda CLIENT SETINFO al conectarse; el ACL lo rechaza y la conexión sigue. Nunca manda PING.
    @Test
    void theConnectionNeverSendsPingAndToleratesARejectedClientSetinfo() {
        redis.admin().aclLogReset();

        assertThat(adapter.readMetadata(redis.integration())).isInstanceOf(RedisReadResult.Received.class);

        List<String> denied = redis.admin().aclLog().stream()
                .map(entry -> String.valueOf(entry.get("object")))
                .toList();
        assertThat(denied).allMatch("client|setinfo"::equals);
    }

    @Test
    void itReadsAVersionWithTheCurrentStateOfEachAffectedEntity() {
        RedisReadResult<CatalogVersionChanges> result = adapter.readVersionChanges(redis.integration(), 59);

        assertThat(result).isEqualTo(new RedisReadResult.Received<>(new CatalogVersionChanges(59,
                List.of(CatalogSnapshots.categories().get(1)),
                List.of(CatalogSnapshots.professionals().getFirst()),
                List.of(CatalogSnapshots.weeklySchedules().get(1)))));
    }

    @Test
    void aVersionWithoutEntitiesIsReadWithoutQueryingTheHashes() {
        redis.putChanges(60, List.of(), List.of(), List.of());

        assertThat(adapter.readVersionChanges(redis.integration(), 60))
                .isEqualTo(new RedisReadResult.Received<>(new CatalogVersionChanges(60, List.of(), List.of(), List.of())));
    }

    @Test
    void missingChangesOrAnIdOutsideItsHashAreInvalid() {
        redis.putChanges(61, List.of(), List.of(999L), List.of());

        assertThat(reason(adapter.readVersionChanges(redis.integration(), 62))).contains("do not exist");
        assertThat(reason(adapter.readVersionChanges(redis.integration(), 61))).contains("professionals 999: not in its hash");
    }

    @Test
    void missingMetadataIsInvalid() {
        redis.admin().del(CatedraRedis.METADATA);

        assertThat(adapter.readMetadata(redis.integration())).isInstanceOf(RedisReadResult.Invalid.class);
    }

    @Test
    void aKeyWithAnotherTypeIsInvalid() {
        redis.admin().del(CatedraRedis.METADATA);
        redis.admin().set(CatedraRedis.METADATA, "not a hash");

        assertThat(reason(adapter.readMetadata(redis.integration()))).contains("WRONGTYPE");
    }

    @Test
    void aWrongPasswordIsRejectedWithoutShowingThePassword(CapturedOutput output) {
        RedisReadResult<CatalogMetadata> result = adapter.readMetadata(
                redis.integrationFor(CatedraRedis.USERNAME, "wrong-password"));

        assertThat(result).isInstanceOf(RedisReadResult.CredentialsRejected.class);
        assertThat(reason(result)).contains("WRONGPASS").doesNotContain(CatedraRedis.USERNAME);
        assertThat(output).doesNotContain("wrong-password").doesNotContain(CatedraRedis.PASSWORD);
    }

    @Test
    void aUserWithoutPermissionOverTheKeysIsRejected() {
        RedisReadResult<CatalogMetadata> result = adapter.readMetadata(
                redis.integrationFor(CatedraRedis.NO_KEYS_USERNAME, CatedraRedis.PASSWORD));

        assertThat(result).isInstanceOf(RedisReadResult.CredentialsRejected.class);
        assertThat(reason(result)).contains("NOPERM").doesNotContain(CatedraRedis.NO_KEYS_USERNAME);
    }

    @Test
    void aNewConnectionIsMadeWhenTheSessionBringsOtherData() {
        assertThat(adapter.readMetadata(redis.integration())).isInstanceOf(RedisReadResult.Received.class);

        assertThat(adapter.readMetadata(redis.integrationFor(CatedraRedis.NO_KEYS_USERNAME, CatedraRedis.PASSWORD)))
                .isInstanceOf(RedisReadResult.CredentialsRejected.class);
        assertThat(adapter.readMetadata(redis.integrationFor(CatedraRedis.USERNAME, "wrong-password")))
                .isInstanceOf(RedisReadResult.CredentialsRejected.class);
        assertThat(adapter.readMetadata(redis.integration())).isInstanceOf(RedisReadResult.Received.class);
    }

    @Test
    void aRedisThatDoesNotAnswerInTimeIsUnavailable() {
        assertThat(adapter.readMetadata(redis.integration())).isInstanceOf(RedisReadResult.Received.class);
        redis.pauseClients(Duration.ofMillis(1500));

        RedisReadResult<CatalogMetadata> result = adapter.readMetadata(redis.integration());

        assertThat(result).isInstanceOf(RedisReadResult.Unavailable.class);
        assertThat(reason(result)).contains("did not answer in time");
    }

    @Test
    void aClosedPortIsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        TechnicalIntegration integration = redis.integration();
        TechnicalIntegration unreachable = new TechnicalIntegration(integration.groupId(), integration.redisHost(),
                closedPort, integration.redisUsername(), integration.redisPassword(),
                integration.kafkaBootstrapServers(), integration.kafkaConsumerGroupId(),
                integration.kafkaCatalogTopic(), integration.provisioningStatus());

        assertThat(adapter.readMetadata(unreachable)).isInstanceOf(RedisReadResult.Unavailable.class);
    }

    private static CatedraProperties properties() {
        return new CatedraProperties("http://localhost", "user", "password", Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                new CatedraProperties.Login(5, Duration.ofSeconds(1), 2, Duration.ofSeconds(30), Duration.ofSeconds(60)),
                new CatedraProperties.Snapshot(Duration.ofSeconds(2), Duration.ofSeconds(2), 5, Duration.ofSeconds(1), 2,
                        Duration.ofSeconds(30)),
                new CatedraProperties.Redis(Duration.ofSeconds(2), COMMAND_TIMEOUT, 3, Duration.ofSeconds(1), 2,
                        Duration.ofSeconds(30)));
    }

    private static String reason(RedisReadResult<?> result) {
        return switch (result) {
            case RedisReadResult.Received<?> received -> "received " + received.value();
            case RedisReadResult.CredentialsRejected<?> rejected -> rejected.reason();
            case RedisReadResult.Unavailable<?> unavailable -> unavailable.reason();
            case RedisReadResult.Invalid<?> invalid -> invalid.reason();
        };
    }
}
