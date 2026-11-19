package catalogo.catedra.application.usecases;

import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogVersionChanges;
import catalogo.catedra.domain.model.RedisReadResult;
import catalogo.catedra.domain.model.TechnicalIntegration;
import catalogo.catedra.domain.ports.out.CatalogRedisClient;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Cliente de Redis falso para os tests.
public final class ScriptedRedisClient implements CatalogRedisClient {

    private final Deque<RedisReadResult<CatalogMetadata>> metadataScript = new ArrayDeque<>();
    private final Deque<RedisReadResult<CatalogVersionChanges>> versionScript = new ArrayDeque<>();
    private final List<TechnicalIntegration> receivedIntegrations = new CopyOnWriteArrayList<>();
    private final List<Long> requestedVersions = new CopyOnWriteArrayList<>();

    public ScriptedRedisClient thenAnswerMetadata(RedisReadResult<CatalogMetadata> result) {
        metadataScript.add(result);
        return this;
    }

    public ScriptedRedisClient thenAnswerVersion(RedisReadResult<CatalogVersionChanges> result) {
        versionScript.add(result);
        return this;
    }

    public List<TechnicalIntegration> receivedIntegrations() {
        return List.copyOf(receivedIntegrations);
    }

    public List<Long> requestedVersions() {
        return List.copyOf(requestedVersions);
    }

    @Override
    public RedisReadResult<CatalogMetadata> readMetadata(TechnicalIntegration integration) {
        receivedIntegrations.add(integration);
        return next(metadataScript);
    }

    @Override
    public RedisReadResult<CatalogVersionChanges> readVersionChanges(TechnicalIntegration integration, long version) {
        receivedIntegrations.add(integration);
        requestedVersions.add(version);
        return next(versionScript);
    }

    private static <T> T next(Deque<T> script) {
        synchronized (script) {
            if (script.isEmpty()) {
                throw new IllegalStateException("The scripted Redis client has no answer configured");
            }
            return script.size() > 1 ? script.poll() : script.peek();
        }
    }
}
