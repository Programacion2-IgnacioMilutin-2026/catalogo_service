package catalogo.catedra.application.usecases;

import catalogo.catedra.TechnicalIntegrations;
import catalogo.catedra.domain.model.TechnicalLoginResult;
import catalogo.catedra.domain.ports.out.TechnicalLoginClient;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// Cliente de login falso para los tests.
public final class ScriptedLoginClient implements TechnicalLoginClient {

    public static final String TOKEN = "test-technical-jwt";

    private final Deque<Supplier<TechnicalLoginResult>> script = new ArrayDeque<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile Runnable beforeEachAnswer = () -> {
    };

    public static TechnicalLoginResult success() {
        return success(TOKEN);
    }

    public static TechnicalLoginResult success(String token) {
        return new TechnicalLoginResult.Success(token, TechnicalIntegrations.complete());
    }

    public ScriptedLoginClient thenAnswer(TechnicalLoginResult result) {
        script.add(() -> result);
        return this;
    }

    public ScriptedLoginClient thenThrow(RuntimeException exception) {
        script.add(() -> {
            throw exception;
        });
        return this;
    }

    public ScriptedLoginClient beforeEachAnswer(Runnable action) {
        this.beforeEachAnswer = action;
        return this;
    }

    public int calls() {
        return calls.get();
    }

    @Override
    public TechnicalLoginResult login() {
        calls.incrementAndGet();
        beforeEachAnswer.run();
        Supplier<TechnicalLoginResult> step;
        synchronized (script) {
            if (script.isEmpty()) {
                throw new IllegalStateException("The scripted login client has no answer configured");
            }
            step = script.size() > 1 ? script.poll() : script.peek();
        }
        return step.get();
    }
}
