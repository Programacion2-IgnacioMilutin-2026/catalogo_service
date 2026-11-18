package catalogo.catalog.infrastructure.persistence.adapter;

import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogSamples;
import catalogo.catalog.CatalogTables;
import catalogo.catalog.domain.model.CatalogSyncState;
import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.out.CatalogSyncStateRepository;
import catalogo.catalog.domain.ports.out.ProfessionalCategoryRepository;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import catalogo.catalog.domain.ports.out.WeeklyScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Time;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Los repositorios de la copia local contra un PostgreSQL real.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CatalogRepositoryAdaptersTest {

    @Autowired
    private ProfessionalCategoryRepository categoryRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private WeeklyScheduleRepository weeklyScheduleRepository;

    @Autowired
    private CatalogSyncStateRepository syncStateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CatalogTables tables;

    @BeforeEach
    void emptyCatalog() {
        tables = new CatalogTables(jdbcTemplate);
        tables.reset();
    }

    @Test
    void savingInsertsNewCategoriesAndUpdatesExistingOnesById() {
        categoryRepository.saveAll(List.of(CatalogSamples.category(1)));
        ProfessionalCategory renamed = CatalogSamples.category(1);
        renamed.setName("Clínica médica");
        renamed.setDescription(null);
        renamed.setEnabled(false);

        categoryRepository.saveAll(List.of(renamed, CatalogSamples.category(2)));

        assertThat(tables.ids("professional_categories")).containsExactly(1L, 2L);
        assertThat(tables.categories().getFirst())
                .containsEntry("name", "Clínica médica")
                .containsEntry("description", null)
                .containsEntry("enabled", false);
    }

    @Test
    void aProfessionalIsSavedWithItsCategoryAndCanMoveToAnotherOne() {
        ProfessionalCategory first = CatalogSamples.category(1);
        ProfessionalCategory second = CatalogSamples.category(2);
        categoryRepository.saveAll(List.of(first, second));
        professionalRepository.saveAll(List.of(CatalogSamples.professional(15, first)));

        professionalRepository.saveAll(List.of(CatalogSamples.professional(15, second)));

        assertThat(tables.professionals()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("id", 15L)
                .containsEntry("category_id", 2L)
                .containsEntry("first_name", "Nombre 15")
                .containsEntry("last_name", "Apellido 15")
                .containsEntry("enabled", true));
    }

    @Test
    void aWeeklyScheduleIsSavedWithItsProfessionalDayAndLocalTimes() {
        ProfessionalCategory category = CatalogSamples.category(1);
        Professional professional = CatalogSamples.professional(15, category);
        categoryRepository.saveAll(List.of(category));
        professionalRepository.saveAll(List.of(professional));

        weeklyScheduleRepository.saveAll(List.of(CatalogSamples.weeklySchedule(21, professional)));

        assertThat(tables.weeklySchedules()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("professional_id", 15L)
                .containsEntry("day_of_week", "MONDAY")
                .containsEntry("start_time", Time.valueOf("09:00:00"))
                .containsEntry("end_time", Time.valueOf("12:00:00"))
                .containsEntry("slot_duration_minutes", 30)
                .containsEntry("enabled", true));
    }

    @Test
    void deletingKeepsOnlyTheListedIds() {
        ProfessionalCategory category = CatalogSamples.category(1);
        List<Professional> professionals = List.of(
                CatalogSamples.professional(15, category), CatalogSamples.professional(16, category));
        categoryRepository.saveAll(List.of(category, CatalogSamples.category(2)));
        professionalRepository.saveAll(professionals);
        weeklyScheduleRepository.saveAll(List.of(
                CatalogSamples.weeklySchedule(21, professionals.get(0)),
                CatalogSamples.weeklySchedule(22, professionals.get(1))));

        weeklyScheduleRepository.deleteAllExcept(List.of(21L));
        professionalRepository.deleteAllExcept(List.of(15L));
        categoryRepository.deleteAllExcept(List.of(1L));

        assertThat(tables.ids("weekly_schedules")).containsExactly(21L);
        assertThat(tables.ids("professionals")).containsExactly(15L);
        assertThat(tables.ids("professional_categories")).containsExactly(1L);
    }

    @Test
    void deletingWithAnEmptyListRemovesEverything() {
        ProfessionalCategory category = CatalogSamples.category(1);
        Professional professional = CatalogSamples.professional(15, category);
        categoryRepository.saveAll(List.of(category));
        professionalRepository.saveAll(List.of(professional));
        weeklyScheduleRepository.saveAll(List.<WeeklySchedule>of(CatalogSamples.weeklySchedule(21, professional)));

        weeklyScheduleRepository.deleteAllExcept(List.of());
        professionalRepository.deleteAllExcept(List.of());
        categoryRepository.deleteAllExcept(List.of());

        assertThat(tables.ids("weekly_schedules")).isEmpty();
        assertThat(tables.ids("professionals")).isEmpty();
        assertThat(tables.ids("professional_categories")).isEmpty();
    }

    @Test
    void theSyncStateStartsEmptyAndKeepsWhatIsSaved() {
        assertThat(syncStateRepository.get()).usingRecursiveComparison().isEqualTo(new CatalogSyncState());

        CatalogSyncState saved = CatalogSamples.failedState();
        syncStateRepository.save(saved);

        assertThat(syncStateRepository.get()).usingRecursiveComparison().isEqualTo(saved);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM catalog_sync_state", Integer.class))
                .isEqualTo(1);
    }
}
