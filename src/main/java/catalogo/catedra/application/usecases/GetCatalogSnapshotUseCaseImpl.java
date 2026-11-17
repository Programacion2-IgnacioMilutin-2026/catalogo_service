package catalogo.catedra.application.usecases;

import catalogo.catedra.domain.model.CatalogSnapshot;
import catalogo.catedra.domain.model.CatalogSnapshotPolicy;
import catalogo.catedra.domain.model.CatalogSnapshotResult;
import catalogo.catedra.domain.model.SnapshotFetchResult;
import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.in.GetCatalogSnapshotUseCase;
import catalogo.catedra.domain.ports.in.GetTechnicalSessionUseCase;
import catalogo.catedra.domain.ports.in.RenewTechnicalSessionUseCase;
import catalogo.catedra.domain.ports.out.CatalogSnapshotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

// Pide el snapshot a la cátedra.
@Slf4j
@Component
@RequiredArgsConstructor
public class GetCatalogSnapshotUseCaseImpl implements GetCatalogSnapshotUseCase {

    private final GetTechnicalSessionUseCase getTechnicalSessionUseCase;
    private final RenewTechnicalSessionUseCase renewTechnicalSessionUseCase;
    private final CatalogSnapshotClient catalogSnapshotClient;
    private final CatalogSnapshotPolicy catalogSnapshotPolicy;

    @Override
    public CatalogSnapshotResult getCatalogSnapshot() {
        Optional<TechnicalSession> session = getTechnicalSessionUseCase.getTechnicalSession()
                .or(() -> renewTechnicalSessionUseCase.renewTechnicalSession(null));
        if (session.isEmpty()) {
            log.warn("Catalog snapshot not requested: the technical integration is not available");
            return new CatalogSnapshotResult.Unavailable("The technical integration is not available");
        }
        TechnicalSession current = session.get();
        int maxAttempts = catalogSnapshotPolicy.maxAttempts();
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
                    return new CatalogSnapshotResult.Unavailable("Interrupted while waiting to request the snapshot");
                }
            }
            repeatNow = false;
            SnapshotFetchResult result = fetchSafely(current.token());
            switch (result) {
                case SnapshotFetchResult.Received received -> {
                    return checked(received.snapshot());
                }
                case SnapshotFetchResult.TokenRejected rejected -> {
                    if (renewed) {
                        log.warn("Catalog snapshot rejected: the renewed technical token was rejected too");
                        return new CatalogSnapshotResult.Rejected("Central API rejected the renewed technical token");
                    }
                    renewed = true;
                    Optional<TechnicalSession> renewedSession = renewTechnicalSessionUseCase.renewTechnicalSession(current);
                    if (renewedSession.isEmpty()) {
                        log.warn("Catalog snapshot not obtained: the technical session could not be renewed");
                        return new CatalogSnapshotResult.Unavailable("The technical session could not be renewed");
                    }
                    log.info("Catalog snapshot: technical token rejected, repeating the request with the renewed session");
                    current = renewedSession.get();
                    repeatNow = true;
                }
                case SnapshotFetchResult.Rejected rejected -> {
                    log.warn("Catalog snapshot rejected: {}", rejected.reason());
                    return new CatalogSnapshotResult.Rejected(rejected.reason());
                }
                case SnapshotFetchResult.Invalid invalid -> {
                    log.warn("Catalog snapshot invalid: {}", invalid.reason());
                    return new CatalogSnapshotResult.Invalid(invalid.reason());
                }
                case SnapshotFetchResult.Unavailable unavailable -> {
                    log.warn("Catalog snapshot attempt {}/{} failed: {}", attempt, maxAttempts, unavailable.reason());
                    lastFailure = unavailable.reason();
                }
            }
        }
        log.warn("Catalog snapshot not obtained after {} attempts", maxAttempts);
        return new CatalogSnapshotResult.Unavailable(
                "Catalog snapshot not obtained after " + maxAttempts + " attempts: " + lastFailure);
    }

    private CatalogSnapshotResult checked(CatalogSnapshot snapshot) {
        List<String> problems = snapshot.problems();
        if (!problems.isEmpty()) {
            log.warn("Catalog snapshot {} is not consistent: {}", snapshot.snapshotVersion(), problems);
            return new CatalogSnapshotResult.Invalid("Inconsistent snapshot: " + problems);
        }
        log.info("Catalog snapshot {} received: {} categories, {} professionals, {} weekly schedules",
                snapshot.snapshotVersion(), snapshot.categories().size(), snapshot.professionals().size(),
                snapshot.weeklySchedules().size());
        return new CatalogSnapshotResult.Obtained(snapshot);
    }

    private boolean waitBefore(int attempt) {
        Duration delay = catalogSnapshotPolicy.delayBeforeAttempt(attempt);
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

    private SnapshotFetchResult fetchSafely(String token) {
        try {
            return catalogSnapshotClient.fetchSnapshot(token);
        } catch (RuntimeException e) {
            log.error("Unexpected error during the snapshot request: {}", e.getClass().getSimpleName());
            return new SnapshotFetchResult.Unavailable("Unexpected error: " + e.getClass().getSimpleName());
        }
    }
}
