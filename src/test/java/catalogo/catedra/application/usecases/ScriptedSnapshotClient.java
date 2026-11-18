package catalogo.catedra.application.usecases;

import catalogo.catedra.domain.model.SnapshotFetchResult;
import catalogo.catedra.domain.ports.out.CatalogSnapshotClient;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

// Cliente del snapshot falso para los tests: responde en orden (la última respuesta se repite) y guarda los JWT recibidos.
public final class ScriptedSnapshotClient implements CatalogSnapshotClient {

    private final Deque<Supplier<SnapshotFetchResult>> script = new ArrayDeque<>();
    private final List<String> receivedTokens = new CopyOnWriteArrayList<>();

    public ScriptedSnapshotClient thenAnswer(SnapshotFetchResult result) {
        script.add(() -> result);
        return this;
    }

    public ScriptedSnapshotClient thenThrow(RuntimeException exception) {
        script.add(() -> {
            throw exception;
        });
        return this;
    }

    public int calls() {
        return receivedTokens.size();
    }

    public List<String> receivedTokens() {
        return List.copyOf(receivedTokens);
    }

    @Override
    public SnapshotFetchResult fetchSnapshot(String technicalToken) {
        receivedTokens.add(technicalToken);
        Supplier<SnapshotFetchResult> step;
        synchronized (script) {
            if (script.isEmpty()) {
                throw new IllegalStateException("The scripted snapshot client has no answer configured");
            }
            step = script.size() > 1 ? script.poll() : script.peek();
        }
        return step.get();
    }
}
