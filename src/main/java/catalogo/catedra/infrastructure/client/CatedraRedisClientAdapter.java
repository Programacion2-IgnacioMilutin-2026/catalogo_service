package catalogo.catedra.infrastructure.client;

import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogVersionChanges;
import catalogo.catedra.domain.model.RedisReadResult;
import catalogo.catedra.domain.model.TechnicalIntegration;
import catalogo.catedra.domain.ports.out.CatalogRedisClient;
import catalogo.catedra.infrastructure.client.dto.CatalogVersionChangesResponse;
import catalogo.catedra.infrastructure.client.mapper.CatalogVersionChangesMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.protocol.ProtocolVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;

// Lee el catálogo publicado en el Redis de la cátedra con Lettuce.
@Component
@RequiredArgsConstructor
public class CatedraRedisClientAdapter implements CatalogRedisClient, DisposableBean {

    private static final String METADATA = "catedra:sync:metadata";
    private static final String CATEGORIES = "catedra:sync:professional-categories";
    private static final String PROFESSIONALS = "catedra:sync:professionals";
    private static final String WEEKLY_SCHEDULES = "catedra:sync:weekly-schedules";

    private final CatedraProperties catedraProperties;
    private final CatalogVersionChangesMapper catalogVersionChangesMapper;

    private TechnicalIntegration connectedWith;
    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;

    @Override
    public RedisReadResult<CatalogMetadata> readMetadata(TechnicalIntegration integration) {
        try {
            Map<String, String> fields = connectionFor(integration).sync().hgetall(METADATA);
            return catalogVersionChangesMapper.metadataOf(fields);
        } catch (RuntimeException e) {
            return classify(e);
        }
    }

    @Override
    public RedisReadResult<CatalogVersionChanges> readVersionChanges(TechnicalIntegration integration, long version) {
        try {
            StatefulRedisConnection<String, String> current = connectionFor(integration);
            String json = current.sync().get("catedra:sync:changes:" + version);
            return switch (catalogVersionChangesMapper.changesOf(version, json)) {
                case RedisReadResult.Received<CatalogVersionChangesResponse> received ->
                        readEntities(current.async(), version, received.value());
                case RedisReadResult.Invalid<CatalogVersionChangesResponse> invalid ->
                        new RedisReadResult.Invalid<>(invalid.reason());
                case RedisReadResult.CredentialsRejected<CatalogVersionChangesResponse> rejected ->
                        new RedisReadResult.CredentialsRejected<>(rejected.reason());
                case RedisReadResult.Unavailable<CatalogVersionChangesResponse> unavailable ->
                        new RedisReadResult.Unavailable<>(unavailable.reason());
            };
        } catch (RuntimeException e) {
            return classify(e);
        }
    }

    private RedisReadResult<CatalogVersionChanges> readEntities(RedisAsyncCommands<String, String> async, long version,
                                                                CatalogVersionChangesResponse changes) {
        CatalogVersionChangesResponse.Changes ids = changes.getChanges();
        RedisFuture<List<KeyValue<String, String>>> categories = hmget(async, CATEGORIES, ids.getProfessionalCategories());
        RedisFuture<List<KeyValue<String, String>>> professionals = hmget(async, PROFESSIONALS, ids.getProfessionals());
        RedisFuture<List<KeyValue<String, String>>> schedules = hmget(async, WEEKLY_SCHEDULES, ids.getWeeklySchedules());
        return catalogVersionChangesMapper.versionChangesOf(version, changes, byId(categories),
                byId(professionals), byId(schedules));
    }

    private static RedisFuture<List<KeyValue<String, String>>> hmget(RedisAsyncCommands<String, String> async,
                                                                     String hash, List<Long> ids) {
        Set<Long> distinct = CatalogVersionChangesMapper.distinctIds(ids);
        if (distinct.isEmpty()) {
            return null;
        }
        return async.hmget(hash, distinct.stream().map(String::valueOf).toArray(String[]::new));
    }

    // Un id que no está en su Hash queda con valor null.
    private static Map<Long, String> byId(RedisFuture<List<KeyValue<String, String>>> future) {
        Map<Long, String> values = new HashMap<>();
        if (future == null) {
            return values;
        }
        try {
            for (KeyValue<String, String> keyValue : future.toCompletableFuture().join()) {
                values.put(Long.valueOf(keyValue.getKey()), keyValue.hasValue() ? keyValue.getValue() : null);
            }
        } catch (CompletionException e) {
            throw e.getCause() instanceof RuntimeException cause ? cause : e;
        }
        return values;
    }

    private synchronized StatefulRedisConnection<String, String> connectionFor(TechnicalIntegration integration) {
        if (connection != null && connection.isOpen() && sameConnectionData(connectedWith, integration)) {
            return connection;
        }
        close();
        CatedraProperties.Redis redis = catedraProperties.redis();
        RedisURI uri = RedisURI.builder()
                .withHost(integration.redisHost())
                .withPort(integration.redisPort())
                .withAuthentication(integration.redisUsername(), integration.redisPassword())
                .withTimeout(redis.commandTimeout())
                .build();
        RedisClient newClient = RedisClient.create(uri);
        newClient.setOptions(ClientOptions.builder()
                .pingBeforeActivateConnection(false)
                .protocolVersion(ProtocolVersion.RESP2)
                .autoReconnect(false)
                .socketOptions(SocketOptions.builder().connectTimeout(redis.connectTimeout()).build())
                .build());
        try {
            connection = newClient.connect();
        } catch (RuntimeException e) {
            newClient.shutdown(Duration.ZERO, Duration.ofSeconds(2));
            throw e;
        }
        client = newClient;
        connectedWith = integration;
        return connection;
    }

    private static boolean sameConnectionData(TechnicalIntegration connected, TechnicalIntegration requested) {
        return connected != null
                && Objects.equals(connected.redisHost(), requested.redisHost())
                && Objects.equals(connected.redisPort(), requested.redisPort())
                && Objects.equals(connected.redisUsername(), requested.redisUsername())
                && Objects.equals(connected.redisPassword(), requested.redisPassword());
    }

    static <T> RedisReadResult<T> classify(RuntimeException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof RedisCommandExecutionException execution) {
                String code = codeOf(execution.getMessage());
                return switch (code) {
                    case "WRONGPASS", "NOAUTH", "NOPERM" ->
                            new RedisReadResult.CredentialsRejected<>("Redis rejected the request: " + code);
                    case "WRONGTYPE" -> new RedisReadResult.Invalid<>("Redis keeps a key with another type: " + code);
                    default -> new RedisReadResult.Unavailable<>("Redis answered an error: " + code);
                };
            }
            if (cause instanceof RedisCommandTimeoutException) {
                return new RedisReadResult.Unavailable<>("Redis did not answer in time");
            }
        }
        return new RedisReadResult.Unavailable<>("Redis is not reachable: " + e.getClass().getSimpleName());
    }

    private static String codeOf(String message) {
        if (message == null || message.isBlank()) {
            return "UNKNOWN";
        }
        int space = message.indexOf(' ');
        return space < 0 ? message : message.substring(0, space);
    }

    @Override
    public synchronized void destroy() {
        close();
    }

    private void close() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
        if (client != null) {
            client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
            client = null;
        }
        connectedWith = null;
    }
}
