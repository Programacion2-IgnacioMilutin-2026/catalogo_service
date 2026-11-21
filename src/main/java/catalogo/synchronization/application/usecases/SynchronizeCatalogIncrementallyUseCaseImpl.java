package catalogo.synchronization.application.usecases;

import catalogo.synchronization.application.support.CatalogCatchUp;
import catalogo.synchronization.application.support.SynchronizationLock;
import catalogo.synchronization.domain.model.CatchUpResult;
import catalogo.synchronization.domain.model.IncrementalSyncResult;
import catalogo.synchronization.domain.model.ProcessedEvent;
import catalogo.synchronization.domain.ports.in.SynchronizeCatalogIncrementallyUseCase;
import catalogo.synchronization.domain.ports.out.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;

// Procesa un aviso de versión nueva para hacer una sincronizacion incremental.
@Slf4j
@Component
@RequiredArgsConstructor
public class SynchronizeCatalogIncrementallyUseCaseImpl implements SynchronizeCatalogIncrementallyUseCase {

    private final ProcessedEventRepository processedEventRepository;
    private final SynchronizationLock synchronizationLock;
    private final CatalogCatchUp catalogCatchUp;
    private final Clock clock;

    @Override
    public IncrementalSyncResult synchronizeCatalogIncrementally(String eventId, long newVersion) {
        String name = "Catalog update notice " + eventId;
        log.info("{} received: newVersion {}", name, newVersion);
        IncrementalSyncResult result = process(name, eventId);
        log.info("{} finished: {}", name, result);
        return result;
    }

    private IncrementalSyncResult process(String name, String eventId) {
        boolean processed;
        try {
            processed = processedEventRepository.findById(eventId).isPresent();
        } catch (RuntimeException e) {
            return failWithoutRecording(name, "the processed notices could not be read", e);
        }
        if (processed) {
            log.info("{} was already processed: nothing to do", name);
            return new IncrementalSyncResult.AlreadyProcessed();
        }
        if (!synchronizationLock.acquire()) {
            log.warn("{}: interrupted while waiting for another synchronization, not processed", name);
            return new IncrementalSyncResult.Failed("Interrupted while waiting for another synchronization");
        }
        CatchUpResult catchUp;
        try {
            catchUp = catalogCatchUp.catchUp(name);
        } catch (RuntimeException e) {
            return failWithoutRecording(name, "the synchronization stopped", e);
        } finally {
            synchronizationLock.release();
        }
        return switch (catchUp) {
            case CatchUpResult.UpToDate upToDate -> {
                register(name, eventId);
                yield new IncrementalSyncResult.UpToDate(upToDate.localVersion());
            }
            case CatchUpResult.Updated updated -> {
                register(name, eventId);
                yield new IncrementalSyncResult.Updated(updated.localVersion());
            }
            case CatchUpResult.Failed failed -> new IncrementalSyncResult.Failed(failed.reason());
        };
    }

    // Un solo intento: si la base no lo acepta, el aviso repetido se procesaría como nuevo, sin daño.
    private void register(String name, String eventId) {
        try {
            processedEventRepository.create(ProcessedEvent.builder()
                    .eventId(eventId)
                    .processedAt(clock.instant())
                    .build());
        } catch (RuntimeException e) {
            log.error("{} could not be registered as processed: {}", name, e.getClass().getSimpleName());
        }
    }

    // Una falla de la base fuera de una versión: no se registra en el estado; la verificación lo recupera.
    private IncrementalSyncResult failWithoutRecording(String name, String what, RuntimeException e) {
        log.error("{}: {}: {}", name, what, e.getClass().getSimpleName());
        return new IncrementalSyncResult.Failed(what + ": " + e.getClass().getSimpleName());
    }
}
