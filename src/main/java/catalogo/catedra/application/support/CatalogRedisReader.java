package catalogo.catedra.application.support;

import catalogo.catedra.domain.model.CatalogRedisPolicy;
import catalogo.catedra.domain.model.RedisReadResult;
import catalogo.catedra.domain.model.TechnicalIntegration;
import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.in.GetTechnicalSessionUseCase;
import catalogo.catedra.domain.ports.in.RenewTechnicalSessionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

// Hace una lectura de Redis.
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogRedisReader {

    private final GetTechnicalSessionUseCase getTechnicalSessionUseCase;
    private final RenewTechnicalSessionUseCase renewTechnicalSessionUseCase;
    private final CatalogRedisPolicy catalogRedisPolicy;

    public <T> RedisReadResult<T> read(String operation, Function<TechnicalIntegration, RedisReadResult<T>> read) {
        Optional<TechnicalSession> session = getTechnicalSessionUseCase.getTechnicalSession()
                .or(() -> renewTechnicalSessionUseCase.renewTechnicalSession(null));
        if (session.isEmpty()) {
            log.warn("Catalog Redis {} not read: the technical integration is not available", operation);
            return new RedisReadResult.Unavailable<>("The technical integration is not available");
        }
        TechnicalSession current = session.get();
        int maxAttempts = catalogRedisPolicy.maxAttempts();
        int attempt = 0;
        boolean renewed = false;
        boolean repeatNow = false;
        String lastFailure = "";
        while (true) {
            if (!repeatNow) {
                attempt++;
                if (attempt > maxAttempts) {
                    break;
                }
                if (!waitBefore(attempt)) {
                    return new RedisReadResult.Unavailable<>("Interrupted while waiting to read Redis");
                }
            }
            repeatNow = false;
            RedisReadResult<T> result = readSafely(read, current.integration());
            switch (result) {
                case RedisReadResult.Received<T> received -> {
                    return received;
                }
                case RedisReadResult.Invalid<T> invalid -> {
                    log.warn("Catalog Redis {} invalid: {}", operation, invalid.reason());
                    return invalid;
                }
                case RedisReadResult.CredentialsRejected<T> rejected -> {
                    if (renewed) {
                        log.warn("Catalog Redis {} rejected again with the renewed session: {}", operation,
                                rejected.reason());
                        return new RedisReadResult.CredentialsRejected<>(
                                "Redis rejected the renewed session: " + rejected.reason());
                    }
                    renewed = true;
                    Optional<TechnicalSession> renewedSession = renewTechnicalSessionUseCase.renewTechnicalSession(current);
                    if (renewedSession.isEmpty()) {
                        log.warn("Catalog Redis {} not read: the technical session could not be renewed", operation);
                        return new RedisReadResult.Unavailable<>("The technical session could not be renewed");
                    }
                    log.info("Catalog Redis {}: {}, repeating the read with the renewed session", operation,
                            rejected.reason());
                    current = renewedSession.get();
                    repeatNow = true;
                }
                case RedisReadResult.Unavailable<T> unavailable -> {
                    log.warn("Catalog Redis {} attempt {}/{} failed: {}", operation, attempt, maxAttempts,
                            unavailable.reason());
                    lastFailure = unavailable.reason();
                }
            }
        }
        log.warn("Catalog Redis {} not read after {} attempts", operation, maxAttempts);
        return new RedisReadResult.Unavailable<>(
                "Redis " + operation + " not read after " + maxAttempts + " attempts: " + lastFailure);
    }

    private boolean waitBefore(int attempt) {
        Duration delay = catalogRedisPolicy.delayBeforeAttempt(attempt);
        if (delay.isZero()) {
            return true;
        }
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static <T> RedisReadResult<T> readSafely(Function<TechnicalIntegration, RedisReadResult<T>> read, TechnicalIntegration integration) {
        try {
            return read.apply(integration);
        } catch (RuntimeException e) {
            log.error("Unexpected error while reading Redis: {}", e.getClass().getSimpleName());
            return new RedisReadResult.Unavailable<>("Unexpected error: " + e.getClass().getSimpleName());
        }
    }
}
