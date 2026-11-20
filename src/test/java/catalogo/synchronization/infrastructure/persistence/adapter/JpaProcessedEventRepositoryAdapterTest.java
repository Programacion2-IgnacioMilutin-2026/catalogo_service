package catalogo.synchronization.infrastructure.persistence.adapter;

import catalogo.TestcontainersConfiguration;
import catalogo.synchronization.domain.model.ProcessedEvent;
import catalogo.synchronization.domain.ports.out.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// El repositorio de los avisos procesados contra un PostgreSQL real.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaProcessedEventRepositoryAdapterTest {

    private static final Instant PROCESSED_AT = Instant.parse("2026-11-19T15:18:51.362Z");

    @Autowired
    private ProcessedEventRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void noProcessedEvents() {
        jdbcTemplate.update("DELETE FROM processed_events");
    }

    @Test
    void anEventIsNotFoundUntilItIsRegistered() {
        String eventId = "81545656-d410-4dd1-8a06-18142a406205";
        assertThat(repository.findById(eventId)).isEmpty();

        ProcessedEvent created = repository.create(event(eventId));

        assertThat(created.getEventId()).isEqualTo(eventId);
        assertThat(repository.findById(eventId)).hasValueSatisfying(found -> {
            assertThat(found.getEventId()).isEqualTo(eventId);
            assertThat(found.getProcessedAt()).isEqualTo(PROCESSED_AT);
        });
    }

    @Test
    void theInstantReachesTheTableInUtc() {
        repository.create(event("event-utc"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_id, processed_at AT TIME ZONE 'UTC' AS processed_at_utc FROM processed_events");

        assertThat(row).containsEntry("event_id", "event-utc");
        assertThat(row.get("processed_at_utc").toString()).isEqualTo("2026-11-19 15:18:51.362");
        OffsetDateTime stored = jdbcTemplate.queryForObject("SELECT processed_at FROM processed_events",
                OffsetDateTime.class);
        assertThat(stored.withOffsetSameInstant(ZoneOffset.UTC).toInstant()).isEqualTo(PROCESSED_AT);
    }

    @Test
    void anEventIdOfTheMaximumLengthIsSaved() {
        String longest = "e".repeat(255);

        repository.create(event(longest));

        assertThat(jdbcTemplate.queryForObject("SELECT length(event_id) FROM processed_events", Integer.class))
                .isEqualTo(255);
        assertThat(repository.findById(longest)).isPresent();
    }

    @Test
    void theTablesOfTheCatalogAreUntouched() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM catalog_sync_state", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name <> 'flyway_schema_history' ORDER BY table_name", String.class))
                .containsExactly("catalog_sync_state", "processed_events", "professional_categories",
                        "professionals", "weekly_schedules");
    }

    private static ProcessedEvent event(String eventId) {
        return ProcessedEvent.builder().eventId(eventId).processedAt(PROCESSED_AT).build();
    }
}
