package catalogo.catalog.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;

// Horario semanal de un profesional.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklySchedule {

    public static final Comparator<WeeklySchedule> BY_DAY_START_AND_ID = Comparator
            .comparing(WeeklySchedule::getDayOfWeek)
            .thenComparing(WeeklySchedule::getStartTime)
            .thenComparing(WeeklySchedule::getId);

    private Long id;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private int slotDurationMinutes;
    private boolean enabled;
    private Professional professional;
}
