package catalogo.catedra.application.usecases;

import catalogo.catedra.domain.model.TechnicalLoginPolicy;
import catalogo.catedra.domain.model.TechnicalLoginResult;
import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.in.RenewTechnicalSessionUseCase;
import catalogo.catedra.domain.ports.out.TechnicalIntegrationListener;
import catalogo.catedra.domain.ports.out.TechnicalLoginClient;
import catalogo.catedra.domain.ports.out.TechnicalSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

// Login técnico a pedido luego de una accion en concreto. Solo uno a la vez.
@Slf4j
@Component
@RequiredArgsConstructor
public class RenewTechnicalSessionUseCaseImpl implements RenewTechnicalSessionUseCase {

    private final TechnicalLoginClient technicalLoginClient;
    private final TechnicalSessionRepository technicalSessionRepository;
    private final TechnicalLoginPolicy technicalLoginPolicy;
    private final Clock clock;
    private final TechnicalIntegrationListener technicalIntegrationListener;

    private final ReentrantLock loginLock = new ReentrantLock();

    @Override
    public Optional<TechnicalSession> renewTechnicalSession(TechnicalSession rejectedSession) {
        loginLock.lock();
        try {
            TechnicalSession current = technicalSessionRepository.get();
            if (current.isEnabled() && !current.equals(rejectedSession)) {
                return Optional.of(current);
            }
            if (!current.canAttemptLogin(clock.instant(), technicalLoginPolicy.minInterval())) {
                return Optional.empty();
            }
            TechnicalSession renewed = attemptLogin();
            technicalSessionRepository.save(renewed);
            if (!renewed.isEnabled()) {
                return Optional.empty();
            }
            notifyEnabled(renewed);
            return Optional.of(renewed);
        } finally {
            loginLock.unlock();
        }
    }

    private TechnicalSession attemptLogin() {
        Instant attemptAt = clock.instant();
        TechnicalLoginResult result = loginSafely();
        return switch (result) {
            case TechnicalLoginResult.Success success when !success.integration().isProvisioned() -> {
                log.warn("Technical login renewal: integration not enabled, provisioningStatus={}",
                        success.integration().provisioningStatus());
                yield TechnicalSession.degraded(attemptAt);
            }
            case TechnicalLoginResult.Success success when !success.integration().missingData().isEmpty() -> {
                log.warn("Technical login renewal: incomplete integration, missing {}", success.integration().missingData());
                yield TechnicalSession.degraded(attemptAt);
            }
            case TechnicalLoginResult.Success success -> {
                log.info("Technical login renewed: groupId={}, provisioningStatus={}",
                        success.integration().groupId(), success.integration().provisioningStatus());
                yield TechnicalSession.enabled(success.token(), success.integration(), attemptAt);
            }
            case TechnicalLoginResult.ConfigurationError error -> {
                log.error("Technical login configuration error, no retries until restart: {}", error.reason());
                yield TechnicalSession.configurationError(attemptAt);
            }
            case TechnicalLoginResult.Unavailable unavailable -> {
                log.warn("Technical login renewal failed: {}", unavailable.reason());
                yield TechnicalSession.degraded(attemptAt);
            }
        };
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
