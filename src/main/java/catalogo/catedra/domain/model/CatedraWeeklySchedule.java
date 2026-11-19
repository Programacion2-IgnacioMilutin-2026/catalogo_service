package catalogo.catedra.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

// Horario semanal tal como lo publica la cátedra, en el snapshot y en Redis.
public record CatedraWeeklySchedule(
        long id,
        long professionalId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int slotDurationMinutes,
        boolean enabled) {
}
