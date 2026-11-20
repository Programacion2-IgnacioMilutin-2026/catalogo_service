package catalogo.synchronization.infrastructure.persistence.mapper;

import catalogo.synchronization.domain.model.ProcessedEvent;
import catalogo.synchronization.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.stereotype.Component;

// Convierte un aviso procesado entre el dominio y la entidad.
@Component
public class ProcessedEventMapper {

    public ProcessedEventEntity toEntity(ProcessedEvent processedEvent) {
        if (processedEvent == null) {
            return null;
        }
        return ProcessedEventEntity.builder()
                .eventId(processedEvent.getEventId())
                .processedAt(processedEvent.getProcessedAt())
                .build();
    }

    public ProcessedEvent toDomainModel(ProcessedEventEntity entity) {
        if (entity == null) {
            return null;
        }
        return ProcessedEvent.builder()
                .eventId(entity.getEventId())
                .processedAt(entity.getProcessedAt())
                .build();
    }
}
