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
    void findingByIdsReturnsOnlyTheOnesThatExistWithEveryField() {
        ProfessionalCategory first = CatalogSamples.category(1);
        ProfessionalCategory second = CatalogSamples.category(2);
        Professional professional = CatalogSamples.professional(15, second);
        categoryRepository.saveAll(List.of(first, second));
        professionalRepository.saveAll(List.of(professional));

        List<ProfessionalCategory> categories = categoryRepository.findAllByIds(List.of(2L, 999L));
        List<Professional> professionals = professionalRepository.findAllByIds(List.of(15L, 999L));

        assertThat(categories).usingRecursiveFieldByFieldElementComparator().containsExactly(second);
        assertThat(professionals).usingRecursiveFieldByFieldElementComparator().containsExactly(professional);
        assertThat(categoryRepository.findAllByIds(List.of())).isEmpty();
    }

    @Test
    void findingOneProfessionalByIdReturnsEveryFieldWithItsWholeCategory() {
        ProfessionalCategory first = CatalogSamples.category(1);
        ProfessionalCategory second = CatalogSamples.category(2);
        Professional professional = CatalogSamples.professional(15, second);
        categoryRepository.saveAll(List.of(first, second));
        professionalRepository.saveAll(List.of(professional, CatalogSamples.professional(16, first)));

        assertThat(professionalRepository.findById(15L)).get().usingRecursiveComparison().isEqualTo(professional);
    }

    @Test
    void findingOneProfessionalThatIsNotInTheCopyReturnsEmpty() {
        ProfessionalCategory category = CatalogSamples.category(1);
        categoryRepository.saveAll(List.of(category));
        professionalRepository.saveAll(List.of(CatalogSamples.professional(15, category)));

        assertThat(professionalRepository.findById(999L)).isEmpty();
        assertThat(professionalRepository.findById(0L)).isEmpty();
        assertThat(professionalRepository.findById(-1L)).isEmpty();
    }

    @Test
    void allCategoriesComeInSpanishAlphabeticalOrderWithTiesById() {
        ProfessionalCategory pediatria = named(1, "pediatría", true);
        ProfessionalCategory nandu = named(2, "Ñandú", true);
        ProfessionalCategory opticaDisabled = named(3, "Óptica", false);
        ProfessionalCategory nutricion = named(4, "Nutrición", true);
        ProfessionalCategory odontologia = named(5, "odontología", true);
        ProfessionalCategory opticaLowercase = named(6, "optica", true);
        ProfessionalCategory psicologia = named(7, "Psicología", false);
        ProfessionalCategory withEnye = named(8, "Nuñez", true);
        ProfessionalCategory withN = named(9, "Nunez", true);
        ProfessionalCategory opticaAgain = named(10, "Óptica", true);
        categoryRepository.saveAll(List.of(pediatria, nandu, opticaDisabled, nutricion, odontologia,
                opticaLowercase, psicologia, withEnye, withN, opticaAgain));

        List<ProfessionalCategory> ordered = categoryRepository.findAllOrderedByName();

        assertThat(ordered).extracting(ProfessionalCategory::getId)
                .containsExactly(9L, 8L, 4L, 2L, 5L, 3L, 6L, 10L, 1L, 7L);
        assertThat(ordered).usingRecursiveFieldByFieldElementComparator().containsExactly(
                withN, withEnye, nutricion, nandu, odontologia,
                opticaDisabled, opticaLowercase, opticaAgain, pediatria, psicologia);
    }

    @Test
    void withoutCategoriesTheOrderedListIsEmpty() {
        assertThat(categoryRepository.findAllOrderedByName()).isEmpty();
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

    private static ProfessionalCategory named(long id, String name, boolean enabled) {
        ProfessionalCategory category = CatalogSamples.category(id);
        category.setName(name);
        category.setEnabled(enabled);
        return category;
    }
}
