package catalogo.catalog.infrastructure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

// Un horario semanal de un profesional.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyScheduleResponse {
    private Long id;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private int slotDurationMinutes;
    private boolean enabled;
}
