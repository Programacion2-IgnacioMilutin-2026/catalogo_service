package catalogo.synchronization.infrastructure.persistence.adapter;

import catalogo.synchronization.domain.model.ProcessedEvent;
import catalogo.synchronization.domain.ports.out.ProcessedEventRepository;
import catalogo.synchronization.infrastructure.persistence.entity.ProcessedEventEntity;
import catalogo.synchronization.infrastructure.persistence.mapper.ProcessedEventMapper;
import catalogo.synchronization.infrastructure.persistence.repository.JpaProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

// Guarda y busca los avisos procesados con JPA.
@Component
@RequiredArgsConstructor
public class JpaProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final JpaProcessedEventRepository jpaProcessedEventRepository;
    private final ProcessedEventMapper processedEventMapper;

    @Override
    public ProcessedEvent create(ProcessedEvent processedEvent) {
        ProcessedEventEntity saved = jpaProcessedEventRepository.save(processedEventMapper.toEntity(processedEvent));
        return processedEventMapper.toDomainModel(saved);
    }

    @Override
    public Optional<ProcessedEvent> findById(String eventId) {
        return jpaProcessedEventRepository.findById(eventId)
                .map(processedEventMapper::toDomainModel);
    }
}
