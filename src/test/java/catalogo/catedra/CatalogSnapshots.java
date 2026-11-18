package catalogo.catedra;

import catalogo.catedra.domain.model.CatalogSnapshot;
import catalogo.catedra.domain.model.SnapshotCategory;
import catalogo.catedra.domain.model.SnapshotProfessional;
import catalogo.catedra.domain.model.SnapshotWeeklySchedule;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

// Snapshots de ejemplo creados a partir de la respuesta real de la catedra.
public final class CatalogSnapshots {

    public static final long VERSION = 57;

    private CatalogSnapshots() {
    }

    public static List<SnapshotCategory> categories() {
        return List.of(
                new SnapshotCategory(501, "Kinesiología",
                        "Rehabilitación física y tratamiento de lesiones musculares", true),
                new SnapshotCategory(502, "Nutrición", "Planes de alimentación y control de peso", true));
    }

    public static List<SnapshotProfessional> professionals() {
        return List.of(
                new SnapshotProfessional(601, 501, "Valentina", "Ibáñez", true),
                new SnapshotProfessional(602, 501, "Tomás", "Ferreyra", false),
                new SnapshotProfessional(603, 502, "Lucía", "Núñez", true));
    }

    public static List<SnapshotWeeklySchedule> weeklySchedules() {
        return List.of(
                schedule(701, 601, DayOfWeek.MONDAY, "08:00", "12:00", 30, true),
                schedule(702, 601, DayOfWeek.THURSDAY, "14:00", "18:00", 30, true),
                schedule(703, 602, DayOfWeek.TUESDAY, "09:00", "13:00", 20, false),
                schedule(704, 603, DayOfWeek.FRIDAY, "10:00", "14:00", 20, true));
    }

    public static CatalogSnapshot valid() {
        return new CatalogSnapshot(VERSION, categories(), professionals(), weeklySchedules());
    }

    public static SnapshotWeeklySchedule schedule(long id, long professionalId, DayOfWeek day, String start,
                                                  String end, int slotMinutes, boolean enabled) {
        return new SnapshotWeeklySchedule(id, professionalId, day, LocalTime.parse(start), LocalTime.parse(end),
                slotMinutes, enabled);
    }
}
