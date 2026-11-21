package catalogo.synchronization.infrastructure.persistence.mapper;

import catalogo.synchronization.domain.model.ProcessedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Ida y vuelta del mapper de los avisos procesados.
class ProcessedEventMapperTest {

    private final ProcessedEventMapper mapper = new ProcessedEventMapper();

    @Test
    void roundTripKeepsEveryField() {
        ProcessedEvent original = ProcessedEvent.builder()
                .eventId("81545656-d410-4dd1-8a06-18142a406205")
                .processedAt(Instant.parse("2026-11-19T15:18:51.362Z"))
                .build();
        assertThat(original).hasNoNullFieldsOrProperties();

        ProcessedEvent result = mapper.toDomainModel(mapper.toEntity(original));

        assertThat(result).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void nullMapsToNull() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomainModel(null)).isNull();
    }
}
