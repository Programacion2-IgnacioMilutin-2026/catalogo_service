package catalogo.catalog.application.usecases;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogSamples;
import catalogo.catalog.CatalogTables;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.in.ReplaceCatalogUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El reemplazo de la copia local contra un PostgreSQL real.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReplaceCatalogUseCaseImplTest {

    @Autowired
    private ReplaceCatalogUseCase replaceCatalogUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CatalogTables tables;

    @BeforeEach
    void emptyCatalog() {
        tables = new CatalogTables(jdbcTemplate);
        tables.reset();
    }

    @Test
    void replacingAnEmptyCatalogStoresTheSnapshotItsVersionAndTheSuccess() {
        ProfessionalCategory clinic = CatalogSamples.category(1);
        ProfessionalCategory disabledCategory = disabled(CatalogSamples.category(2));
        Professional ana = CatalogSamples.professional(15, clinic);
        Professional disabledProfessional = disabled(CatalogSamples.professional(16, disabledCategory));
        Professional luis = CatalogSamples.professional(17, clinic);
        Instant before = Instant.now();

        replaceCatalogUseCase.replaceCatalog(303, List.of(clinic, disabledCategory),
                List.of(ana, disabledProfessional, luis),
                List.of(CatalogSamples.weeklySchedule(21, ana), CatalogSamples.weeklySchedule(22, ana),
                        disabled(CatalogSamples.weeklySchedule(23, disabledProfessional)),
                        CatalogSamples.weeklySchedule(24, luis)));

        assertThat(tables.ids("professional_categories")).containsExactly(1L, 2L);
        assertThat(tables.ids("professionals")).containsExactly(15L, 16L, 17L);
        assertThat(tables.ids("weekly_schedules")).containsExactly(21L, 22L, 23L, 24L);
        assertThat(tables.categories().get(1)).containsEntry("enabled", false);
        assertThat(tables.professionals().get(1)).containsEntry("enabled", false).containsEntry("category_id", 2L);
        assertThat(tables.weeklySchedules().get(2)).containsEntry("enabled", false);
        Map<String, Object> state = tables.syncState();
        assertThat(state)
                .containsEntry("local_version", 303L)
                .containsEntry("last_sync_type", "FULL")
                .containsEntry("last_sync_result", "SUCCESS")
                .containsEntry("last_error_type", null);
        Instant lastSyncAt = instant(state, "last_sync_at");
        assertThat(lastSyncAt).isBetween(before, Instant.now());
        assertThat(instant(state, "last_successful_sync_at")).isEqualTo(lastSyncAt);
    }

    @Test
    void replacingAnExistingCopyLeavesItExactlyLikeTheNewSnapshot() {
        ProfessionalCategory first = CatalogSamples.category(1);
        ProfessionalCategory second = CatalogSamples.category(2);
        Professional p15 = CatalogSamples.professional(15, first);
        Professional p16 = CatalogSamples.professional(16, second);
        Professional p17 = CatalogSamples.professional(17, first);
        replaceCatalogUseCase.replaceCatalog(303, List.of(first, second), List.of(p15, p16, p17),
                List.of(CatalogSamples.weeklySchedule(21, p15), CatalogSamples.weeklySchedule(22, p16),
                        CatalogSamples.weeklySchedule(23, p17)));

        ProfessionalCategory renamedFirst = CatalogSamples.category(1);
        renamedFirst.setName("Clínica médica");
        ProfessionalCategory third = CatalogSamples.category(3);
        Professional movedAndDisabled = disabled(CatalogSamples.professional(15, third));
        Professional sameP17 = CatalogSamples.professional(17, renamedFirst);
        Professional p18 = CatalogSamples.professional(18, renamedFirst);
        WeeklySchedule changedHours = CatalogSamples.weeklySchedule(21, movedAndDisabled);
        changedHours.setStartTime(LocalTime.of(14, 0));
        changedHours.setEndTime(LocalTime.of(18, 0));
        changedHours.setSlotDurationMinutes(20);

        replaceCatalogUseCase.replaceCatalog(304, List.of(renamedFirst, third),
                List.of(movedAndDisabled, sameP17, p18),
                List.of(changedHours, CatalogSamples.weeklySchedule(23, sameP17), CatalogSamples.weeklySchedule(24, p18)));

        assertThat(tables.ids("professional_categories")).containsExactly(1L, 3L);
        assertThat(tables.ids("professionals")).containsExactly(15L, 17L, 18L);
        assertThat(tables.ids("weekly_schedules")).containsExactly(21L, 23L, 24L);
        assertThat(tables.categories().getFirst()).containsEntry("name", "Clínica médica");
        assertThat(tables.professionals().getFirst())
                .containsEntry("category_id", 3L)
                .containsEntry("enabled", false);
        assertThat(tables.weeklySchedules().getFirst())
                .containsEntry("start_time", Time.valueOf("14:00:00"))
                .containsEntry("end_time", Time.valueOf("18:00:00"))
                .containsEntry("slot_duration_minutes", 20);
        assertThat(tables.syncState()).containsEntry("local_version", 304L);
    }

    @Test
    void aDatabaseFailureInTheMiddleOfTheReplacementLeavesTheCopyAndTheStateAsTheyWere() {
        ProfessionalCategory category = CatalogSamples.category(1);
        Professional professional = CatalogSamples.professional(15, category);
        replaceCatalogUseCase.replaceCatalog(303, List.of(category), List.of(professional),
                List.of(CatalogSamples.weeklySchedule(21, professional)));
        List<Map<String, Object>> categoriesBefore = tables.categories();
        List<Map<String, Object>> professionalsBefore = tables.professionals();
        List<Map<String, Object>> schedulesBefore = tables.weeklySchedules();
        Map<String, Object> stateBefore = tables.syncState();
        ProfessionalCategory renamed = CatalogSamples.category(1);
        renamed.setName("Otro nombre");
        Professional newProfessional = CatalogSamples.professional(16, renamed);

        jdbcTemplate.execute("CREATE FUNCTION fail_on_schedule_999() RETURNS trigger AS $$ BEGIN "
                + "IF NEW.id = 999 THEN RAISE EXCEPTION 'forced failure'; END IF; RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbcTemplate.execute("CREATE TRIGGER fail_on_schedule_999 BEFORE INSERT ON weekly_schedules "
                + "FOR EACH ROW EXECUTE FUNCTION fail_on_schedule_999()");
        try {
            assertThatThrownBy(() -> replaceCatalogUseCase.replaceCatalog(304, List.of(renamed),
                    List.of(CatalogSamples.professional(15, renamed), newProfessional),
                    List.of(CatalogSamples.weeklySchedule(999, newProfessional))))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER fail_on_schedule_999 ON weekly_schedules");
            jdbcTemplate.execute("DROP FUNCTION fail_on_schedule_999()");
        }

        assertThat(tables.categories()).isEqualTo(categoriesBefore);
        assertThat(tables.professionals()).isEqualTo(professionalsBefore);
        assertThat(tables.weeklySchedules()).isEqualTo(schedulesBefore);
        assertThat(tables.syncState()).isEqualTo(stateBefore);
    }

    private static ProfessionalCategory disabled(ProfessionalCategory category) {
        category.setEnabled(false);
        return category;
    }

    private static Professional disabled(Professional professional) {
        professional.setEnabled(false);
        return professional;
    }

    private static WeeklySchedule disabled(WeeklySchedule weeklySchedule) {
        weeklySchedule.setEnabled(false);
        return weeklySchedule;
    }

    private static Instant instant(Map<String, Object> row, String column) {
        return ((Timestamp) row.get(column)).toInstant();
    }
}
