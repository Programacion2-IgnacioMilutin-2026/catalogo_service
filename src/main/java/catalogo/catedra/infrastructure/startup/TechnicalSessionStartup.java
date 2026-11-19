package catalogo.catedra.infrastructure.startup;

import catalogo.catedra.application.service.TechnicalSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Dispara el login técnico cuando el servicio terminó de arrancar. Asincronico, el servicio no lo espera.
@Component
@RequiredArgsConstructor
public class TechnicalSessionStartup {

    private final TechnicalSessionService technicalSessionService;

    @EventListener(ApplicationReadyEvent.class)
    public void startTechnicalSession() {
        Thread.ofVirtual().name("technical-login").start(technicalSessionService::startTechnicalSession);
    }
}
