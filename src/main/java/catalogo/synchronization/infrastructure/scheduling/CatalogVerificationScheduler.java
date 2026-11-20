package catalogo.synchronization.infrastructure.scheduling;

import catalogo.synchronization.application.service.SynchronizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Dispara la verificación de la versión del catálogo.
@Component
@ConditionalOnProperty(name = "catalogo.synchronization.verification.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class CatalogVerificationScheduler {

    private final SynchronizationService synchronizationService;

    @Scheduled(fixedDelayString = "${catalogo.synchronization.verification.interval:5m}")
    public void verifyCatalogVersion() {
        synchronizationService.verifyCatalogVersion();
    }
}
