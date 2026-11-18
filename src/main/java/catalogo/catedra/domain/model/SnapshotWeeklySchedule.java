package catalogo.catedra.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

// Horario semanal tal como lo entrega el snapshot de la cátedra.
public record SnapshotWeeklySchedule(
        long id,
        long professionalId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int slotDurationMinutes,
        boolean enabled) {
}
