package catalogo.catalog.application.usecases;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogSamples;
import catalogo.catalog.CatalogTables;
import catalogo.catalog.domain.model.ApplyCatalogVersionResult;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.in.ApplyCatalogVersionUseCase;
import catalogo.catalog.domain.ports.in.ReplaceCatalogUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// La aplicación de una versión incremental contra un PostgreSQL real.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApplyCatalogVersionUseCaseImplTest {

    @Autowired
    private ApplyCatalogVersionUseCase applyCatalogVersionUseCase;

    @Autowired
    private ReplaceCatalogUseCase replaceCatalogUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CatalogTables tables;

    @BeforeEach
    void copyInVersion10() {
        tables = new CatalogTables(jdbcTemplate);
        tables.reset();
        ProfessionalCategory first = CatalogSamples.category(1);
        ProfessionalCategory second = CatalogSamples.category(2);
        second.setEnabled(false);
        Professional fifteen = CatalogSamples.professional(15, first);
        Professional seventeen = CatalogSamples.professional(17, first);
        replaceCatalogUseCase.replaceCatalog(10, List.of(first, second), List.of(fifteen, seventeen),
                List.of(CatalogSamples.weeklySchedule(21, fifteen)));
    }

    @Test
    void aVersionIsAppliedWithReferencesToTheCopyAndToTheSameVersionWithoutRemovingAnything() {
        Timestamp previousSuccess = (Timestamp) tables.syncState().get("last_successful_sync_at");
        ProfessionalCategory renamed = CatalogSamples.category(1);
        renamed.setName("Clínica renombrada");
        ProfessionalCategory third = CatalogSamples.category(3);
        Professional fifteenDisabled = professional(15, categoryRef(3));
        fifteenDisabled.setEnabled(false);
        Professional sixteen = professional(16, categoryRef(2));
        WeeklySchedule twentyOneMoved = schedule(21, professionalRef(15));
        twentyOneMoved.setStartTime(LocalTime.of(10, 0));
        WeeklySchedule twentyTwo = schedule(22, professionalRef(16));
        WeeklySchedule twentyThree = schedule(23, professionalRef(17));

        ApplyCatalogVersionResult result = applyCatalogVersionUseCase.applyCatalogVersion(11, List.of(renamed, third),
                List.of(fifteenDisabled, sixteen), List.of(twentyOneMoved, twentyTwo, twentyThree));

        assertThat(result).isEqualTo(new ApplyCatalogVersionResult.Applied(11));
        assertThat(tables.ids("professional_categories")).containsExactly(1L, 2L, 3L);
        assertThat(row(tables.categories(), 1)).containsEntry("name", "Clínica renombrada");
        assertThat(row(tables.categories(), 2))
                .containsEntry("name", "Categoría 2")
                .containsEntry("description", "Descripción de la categoría 2")
                .containsEntry("enabled", false);
        assertThat(tables.ids("professionals")).containsExactly(15L, 16L, 17L);
        assertThat(row(tables.professionals(), 15)).containsEntry("enabled", false).containsEntry("category_id", 3L);
        assertThat(row(tables.professionals(), 16)).containsEntry("category_id", 2L);
        assertThat(row(tables.professionals(), 17))
                .containsEntry("first_name", "Nombre 17")
                .containsEntry("category_id", 1L);
        assertThat(tables.ids("weekly_schedules")).containsExactly(21L, 22L, 23L);
        assertThat(row(tables.weeklySchedules(), 21)).containsEntry("start_time", Time.valueOf("10:00:00"));
        assertThat(row(tables.weeklySchedules(), 22)).containsEntry("professional_id", 16L);
        assertThat(row(tables.weeklySchedules(), 23)).containsEntry("professional_id", 17L);
        Map<String, Object> state = tables.syncState();
        assertThat(state)
                .containsEntry("local_version", 11L)
                .containsEntry("last_sync_type", "INCREMENTAL")
                .containsEntry("last_sync_result", "SUCCESS")
                .containsEntry("last_error_type", null);
        assertThat(state.get("last_sync_at")).isEqualTo(state.get("last_successful_sync_at"));
        assertThat(((Timestamp) state.get("last_successful_sync_at")).toInstant())
                .isAfterOrEqualTo(previousSuccess.toInstant());
    }

    @Test
    void aProfessionalWithAMissingCategoryStopsTheVersionWithoutWritingAnything() {
        Snapshot before = snapshot();
        ProfessionalCategory renamed = CatalogSamples.category(1);
        renamed.setName("No debería guardarse");

        ApplyCatalogVersionResult result = applyCatalogVersionUseCase.applyCatalogVersion(11, List.of(renamed),
                List.of(professional(18, categoryRef(999))), List.of());

        assertThat(result).isEqualTo(new ApplyCatalogVersionResult.NotVerifiable(
                List.of("professional 18 references the missing professional category 999")));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void aScheduleWithAMissingProfessionalStopsTheVersionWithoutWritingAnything() {
        Snapshot before = snapshot();

        ApplyCatalogVersionResult result = applyCatalogVersionUseCase.applyCatalogVersion(11, List.of(),
                List.of(professional(16, categoryRef(1))), List.of(schedule(24, professionalRef(999))));

        assertThat(result).isEqualTo(new ApplyCatalogVersionResult.NotVerifiable(
                List.of("weekly schedule 24 references the missing professional 999")));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void aDatabaseFailureInTheMiddleOfTheVersionLeavesTheCopyAndTheStateAsTheyWere() {
        Snapshot before = snapshot();
        ProfessionalCategory renamed = CatalogSamples.category(1);
        renamed.setName("No debería quedar");

        jdbcTemplate.execute("CREATE FUNCTION fail_on_schedule_999() RETURNS trigger AS $$ BEGIN "
                + "IF NEW.id = 999 THEN RAISE EXCEPTION 'forced failure'; END IF; RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbcTemplate.execute("CREATE TRIGGER fail_on_schedule_999 BEFORE INSERT ON weekly_schedules "
                + "FOR EACH ROW EXECUTE FUNCTION fail_on_schedule_999()");
        try {
            assertThatThrownBy(() -> applyCatalogVersionUseCase.applyCatalogVersion(11, List.of(renamed),
                    List.of(professional(16, categoryRef(1))), List.of(schedule(999, professionalRef(16)))))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER fail_on_schedule_999 ON weekly_schedules");
            jdbcTemplate.execute("DROP FUNCTION fail_on_schedule_999()");
        }

        assertThat(snapshot()).isEqualTo(before);
    }

    private Snapshot snapshot() {
        return new Snapshot(tables.categories(), tables.professionals(), tables.weeklySchedules(), tables.syncState());
    }

    private record Snapshot(List<Map<String, Object>> categories, List<Map<String, Object>> professionals,
                            List<Map<String, Object>> weeklySchedules, Map<String, Object> state) {
    }

    private static Map<String, Object> row(List<Map<String, Object>> rows, long id) {
        return rows.stream().filter(row -> ((Long) row.get("id")) == id).findFirst().orElseThrow();
    }

    // Una referencia solo con el id, como la manda la sincronización.
    private static ProfessionalCategory categoryRef(long id) {
        return ProfessionalCategory.builder().id(id).build();
    }

    private static Professional professionalRef(long id) {
        return Professional.builder().id(id).build();
    }

    private static Professional professional(long id, ProfessionalCategory category) {
        return CatalogSamples.professional(id, category);
    }

    private static WeeklySchedule schedule(long id, Professional professional) {
        return CatalogSamples.weeklySchedule(id, professional);
    }
}
