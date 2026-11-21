package catalogo.synchronization.domain.ports.out;

import catalogo.synchronization.domain.model.ProcessedEvent;

import java.util.Optional;

// Los avisos ya procesados.
public interface ProcessedEventRepository {

    ProcessedEvent create(ProcessedEvent processedEvent);

    Optional<ProcessedEvent> findById(String eventId);
}
