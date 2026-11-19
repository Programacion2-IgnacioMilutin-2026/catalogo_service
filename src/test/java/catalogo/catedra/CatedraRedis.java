package catalogo.catedra;

import catalogo.catedra.domain.model.ProvisioningStatus;
import catalogo.catedra.domain.model.TechnicalIntegration;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Redis de la cátedra simulado.
public final class CatedraRedis implements AutoCloseable {

    public static final String USERNAME = "grp_test";
    public static final String PASSWORD = TechnicalIntegrations.REDIS_PASSWORD;
    public static final String NO_KEYS_USERNAME = "grp_without_keys";

    public static final String METADATA = "catedra:sync:metadata";
    public static final String CATEGORIES = "catedra:sync:professional-categories";
    public static final String PROFESSIONALS = "catedra:sync:professionals";
    public static final String WEEKLY_SCHEDULES = "catedra:sync:weekly-schedules";

    private final GenericContainer<?> container;
    private final RedisClient adminClient;
    private final StatefulRedisConnection<String, String> adminConnection;

    private CatedraRedis(GenericContainer<?> container) {
        this.container = container;
        this.adminClient = RedisClient.create(RedisURI.builder()
                .withHost(container.getHost())
                .withPort(container.getMappedPort(6379))
                .build());
        this.adminConnection = adminClient.connect();
    }

    public static CatedraRedis start() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);
        container.start();
        createUser(container, USERNAME, "~catedra:sync:*");
        createUser(container, NO_KEYS_USERNAME, "~other:*");
        return new CatedraRedis(container);
    }

    private static void createUser(GenericContainer<?> container, String username, String keys) {
        runCli(container, "ACL", "SETUSER", username, "on", ">" + PASSWORD, keys, "-@all", "+@read", "+hello");
    }

    public String cliAsCatedraUser(String... command) {
        String[] withUser = new String[command.length + 4];
        withUser[0] = "--user";
        withUser[1] = USERNAME;
        withUser[2] = "--pass";
        withUser[3] = PASSWORD;
        System.arraycopy(command, 0, withUser, 4, command.length);
        return runCli(container, withUser);
    }

    private static String runCli(GenericContainer<?> container, String... arguments) {
        String[] command = new String[arguments.length + 1];
        command[0] = "redis-cli";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        try {
            Container.ExecResult result = container.execInContainer(command);
            return result.getStdout().trim();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public String host() {
        return container.getHost();
    }

    public int port() {
        return container.getMappedPort(6379);
    }

    // La integración que entregaría el login técnico, apuntando a este Redis.
    public TechnicalIntegration integration() {
        return integrationFor(USERNAME, PASSWORD);
    }

    public TechnicalIntegration integrationFor(String username, String password) {
        TechnicalIntegration complete = TechnicalIntegrations.complete();
        return new TechnicalIntegration(complete.groupId(), host(), port(), username, password,
                complete.kafkaBootstrapServers(), complete.kafkaConsumerGroupId(), complete.kafkaCatalogTopic(),
                ProvisioningStatus.PROVISIONED);
    }

    public RedisCommands<String, String> admin() {
        return adminConnection.sync();
    }

    public void clear() {
        admin().flushall();
    }

    public void putMetadata(long currentVersion, long oldestAvailableVersion) {
        admin().hset(METADATA, Map.of(
                "currentVersion", Long.toString(currentVersion),
                "oldestAvailableVersion", Long.toString(oldestAvailableVersion),
                "publishedAt", "2026-11-18T04:40:15.123456789Z"));
    }

    public void putChanges(long version, List<Long> categories, List<Long> professionals, List<Long> weeklySchedules) {
        admin().set(changesKey(version), "{\"version\":" + version + ",\"changes\":{\"professionalCategories\":"
                + ids(categories) + ",\"professionals\":" + ids(professionals) + ",\"weeklySchedules\":"
                + ids(weeklySchedules) + "}}");
    }

    public void putRaw(String key, String value) {
        admin().set(key, value);
    }

    public void putEntity(String hash, long id, String json) {
        admin().hset(hash, Long.toString(id), json);
    }

    public void putSnapshotEntities() {
        ObjectNode snapshot = FakeCatedraServer.snapshotTree();
        putEach(CATEGORIES, snapshot.get("professionalCategories"));
        putEach(PROFESSIONALS, snapshot.get("professionals"));
        putEach(WEEKLY_SCHEDULES, snapshot.get("weeklySchedules"));
    }

    public static ObjectNode snapshotEntity(String collection, long id) {
        for (JsonNode entity : FakeCatedraServer.snapshotTree().get(collection)) {
            if (entity.get("id").asLong() == id) {
                return (ObjectNode) entity;
            }
        }
        throw new IllegalArgumentException("No " + collection + " with id " + id + " in the example snapshot");
    }

    public void pauseClients(Duration duration) {
        admin().clientPause(duration.toMillis());
    }

    public void stop() {
        container.stop();
    }

    public static String changesKey(long version) {
        return "catedra:sync:changes:" + version;
    }

    private void putEach(String hash, JsonNode entities) {
        for (JsonNode entity : entities) {
            putEntity(hash, entity.get("id").asLong(), entity.toString());
        }
    }

    private static String ids(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    public void close() {
        adminConnection.close();
        adminClient.shutdown();
        container.stop();
    }
}
