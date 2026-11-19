package catalogo.catedra.domain.model;

import catalogo.catedra.CatalogSnapshots;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSnapshotTest {

    @Test
    void aCompleteSnapshotHasNoProblems() {
        assertThat(CatalogSnapshots.valid().problems()).isEmpty();
    }

    @Test
    void referencesToDisabledEntitiesAreValid() {
        CatalogSnapshot snapshot = new CatalogSnapshot(1,
                List.of(new CatedraCategory(1, "Cerrada", null, false)),
                List.of(new CatedraProfessional(10, 1, "Ana", "Gómez", false)),
                List.of(CatalogSnapshots.schedule(100, 10, DayOfWeek.MONDAY, "09:00", "12:00", 30, false)));

        assertThat(snapshot.problems()).isEmpty();
    }

    @Test
    void aSnapshotWithoutCategoriesIsRejected() {
        CatalogSnapshot snapshot = new CatalogSnapshot(1, List.of(), List.of(), List.of());

        assertThat(snapshot.problems()).containsExactly("no professional categories");
    }

    @Test
    void aSnapshotWithCategoriesButNoProfessionalsOrSchedulesIsValid() {
        CatalogSnapshot snapshot = new CatalogSnapshot(1, CatalogSnapshots.categories(), List.of(), List.of());

        assertThat(snapshot.problems()).isEmpty();
    }

    @Test
    void aSnapshotWithProfessionalsButNoSchedulesIsValid() {
        CatalogSnapshot snapshot = new CatalogSnapshot(1, CatalogSnapshots.categories(),
                CatalogSnapshots.professionals(), List.of());

        assertThat(snapshot.problems()).isEmpty();
    }

    @Test
    void aRepeatedIdInAnyCollectionIsAProblem() {
        List<CatedraCategory> categories = new ArrayList<>(CatalogSnapshots.categories());
        categories.add(new CatedraCategory(501, "Otra", null, true));
        List<CatedraProfessional> professionals = new ArrayList<>(CatalogSnapshots.professionals());
        professionals.add(new CatedraProfessional(603, 502, "Otro", "Nombre", true));
        List<CatedraWeeklySchedule> schedules = new ArrayList<>(CatalogSnapshots.weeklySchedules());
        schedules.add(CatalogSnapshots.schedule(704, 603, DayOfWeek.SATURDAY, "09:00", "10:00", 30, true));

        CatalogSnapshot snapshot = new CatalogSnapshot(1, categories, professionals, schedules);

        assertThat(snapshot.problems()).containsExactlyInAnyOrder(
                "repeated professional category id 501",
                "repeated professional id 603",
                "repeated weekly schedule id 704");
    }

    @Test
    void aProfessionalWithAMissingCategoryIsAProblem() {
        List<CatedraProfessional> professionals = new ArrayList<>(CatalogSnapshots.professionals());
        professionals.add(new CatedraProfessional(604, 999, "Sin", "Categoría", true));

        CatalogSnapshot snapshot = new CatalogSnapshot(1, CatalogSnapshots.categories(), professionals,
                CatalogSnapshots.weeklySchedules());

        assertThat(snapshot.problems())
                .containsExactly("professional 604 references the missing professional category 999");
    }

    @Test
    void aScheduleWithAMissingProfessionalIsAProblem() {
        List<CatedraWeeklySchedule> schedules = new ArrayList<>(CatalogSnapshots.weeklySchedules());
        schedules.add(CatalogSnapshots.schedule(705, 999, DayOfWeek.FRIDAY, "09:00", "10:00", 30, true));

        CatalogSnapshot snapshot = new CatalogSnapshot(1, CatalogSnapshots.categories(),
                CatalogSnapshots.professionals(), schedules);

        assertThat(snapshot.problems()).containsExactly("weekly schedule 705 references the missing professional 999");
    }

    @Test
    void theCollectionsCannotBeChangedAfterReading() {
        List<CatedraCategory> categories = new ArrayList<>(CatalogSnapshots.categories());
        CatalogSnapshot snapshot = new CatalogSnapshot(1, categories, List.of(), List.of());

        categories.clear();

        assertThat(snapshot.categories()).hasSize(2);
    }
}
