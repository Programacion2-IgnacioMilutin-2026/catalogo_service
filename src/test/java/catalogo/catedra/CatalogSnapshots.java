package catalogo.catedra;

import catalogo.catedra.domain.model.CatalogSnapshot;
import catalogo.catedra.domain.model.CatedraCategory;
import catalogo.catedra.domain.model.CatedraProfessional;
import catalogo.catedra.domain.model.CatedraWeeklySchedule;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

// Snapshots de ejemplo creados a partir de la respuesta real de la catedra.
public final class CatalogSnapshots {

    public static final long VERSION = 57;

    private CatalogSnapshots() {
    }

    public static List<CatedraCategory> categories() {
        return List.of(
                new CatedraCategory(501, "Kinesiología",
                        "Rehabilitación física y tratamiento de lesiones musculares", true),
                new CatedraCategory(502, "Nutrición", "Planes de alimentación y control de peso", true));
    }

    public static List<CatedraProfessional> professionals() {
        return List.of(
                new CatedraProfessional(601, 501, "Valentina", "Ibáñez", true),
                new CatedraProfessional(602, 501, "Tomás", "Ferreyra", false),
                new CatedraProfessional(603, 502, "Lucía", "Núñez", true));
    }

    public static List<CatedraWeeklySchedule> weeklySchedules() {
        return List.of(
                schedule(701, 601, DayOfWeek.MONDAY, "08:00", "12:00", 30, true),
                schedule(702, 601, DayOfWeek.THURSDAY, "14:00", "18:00", 30, true),
                schedule(703, 602, DayOfWeek.TUESDAY, "09:00", "13:00", 20, false),
                schedule(704, 603, DayOfWeek.FRIDAY, "10:00", "14:00", 20, true));
    }

    public static CatalogSnapshot valid() {
        return new CatalogSnapshot(VERSION, categories(), professionals(), weeklySchedules());
    }

    public static CatedraWeeklySchedule schedule(long id, long professionalId, DayOfWeek day, String start,
                                                  String end, int slotMinutes, boolean enabled) {
        return new CatedraWeeklySchedule(id, professionalId, day, LocalTime.parse(start), LocalTime.parse(end),
                slotMinutes, enabled);
    }
}
