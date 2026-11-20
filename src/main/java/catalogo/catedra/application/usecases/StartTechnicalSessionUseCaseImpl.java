package catalogo.catedra.application.usecases;

import catalogo.catedra.domain.model.TechnicalLoginPolicy;
import catalogo.catedra.domain.model.TechnicalLoginResult;
import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.in.StartTechnicalSessionUseCase;
import catalogo.catedra.domain.ports.out.TechnicalIntegrationListener;
import catalogo.catedra.domain.ports.out.TechnicalLoginClient;
import catalogo.catedra.domain.ports.out.TechnicalSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

// Login técnico inicial.
@Slf4j
@Component
@RequiredArgsConstructor
public class StartTechnicalSessionUseCaseImpl implements StartTechnicalSessionUseCase {

    private final TechnicalLoginClient technicalLoginClient;
    private final TechnicalSessionRepository technicalSessionRepository;
    private final TechnicalLoginPolicy technicalLoginPolicy;
    private final Clock clock;
    private final TechnicalIntegrationListener technicalIntegrationListener;

    // Nunca coinciden dos logins
    @Override
    public TechnicalSession startTechnicalSession() {
        technicalSessionRepository.save(TechnicalSession.connecting());
        int maxAttempts = technicalLoginPolicy.maxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (!waitBefore(attempt)) {
                break;
            }
            TechnicalLoginResult result = loginSafely();
            switch (result) {
                case TechnicalLoginResult.Success success when !success.integration().isProvisioned() -> log.warn(
                        "Technical login attempt {}/{}: integration not enabled, provisioningStatus={}",
                        attempt, maxAttempts, success.integration().provisioningStatus());
                case TechnicalLoginResult.Success success when !success.integration().missingData().isEmpty() -> log.warn(
                        "Technical login attempt {}/{}: incomplete integration, missing {}",
                        attempt, maxAttempts, success.integration().missingData());
                case TechnicalLoginResult.Success success -> {
                    log.info("Technical login succeeded: groupId={}, provisioningStatus={}",
                            success.integration().groupId(), success.integration().provisioningStatus());
                    TechnicalSession enabled = save(TechnicalSession.enabled(success.token(), success.integration(),
                            clock.instant()));
                    notifyEnabled(enabled);
                    return enabled;
                }
                case TechnicalLoginResult.ConfigurationError error -> {
                    log.error("Technical login configuration error, no retries until restart: {}", error.reason());
                    return save(TechnicalSession.configurationError(clock.instant()));
                }
                case TechnicalLoginResult.Unavailable unavailable -> log.warn(
                        "Technical login attempt {}/{} failed: {}", attempt, maxAttempts, unavailable.reason());
            }
        }
        log.warn("Technical login not completed after {} attempts: integration degraded", maxAttempts);
        return save(TechnicalSession.degraded(clock.instant()));
    }

    private boolean waitBefore(int attempt) {
        Duration delay = technicalLoginPolicy.delayBeforeAttempt(attempt);
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

    private TechnicalSession save(TechnicalSession technicalSession) {
        technicalSessionRepository.save(technicalSession);
        return technicalSession;
    }

    private void notifyEnabled(TechnicalSession session) {
        try {
            technicalIntegrationListener.integrationEnabled(session.integration());
        } catch (RuntimeException e) {
            log.error("The enabled integration could not be notified: {}", e.getClass().getSimpleName());
        }
    }

    private TechnicalLoginResult loginSafely() {
        try {
            return technicalLoginClient.login();
        } catch (RuntimeException e) {
            log.error("Unexpected error during the technical login: {}", e.getClass().getSimpleName());
            return new TechnicalLoginResult.Unavailable("Unexpected error: " + e.getClass().getSimpleName());
        }
    }
}
