package catalogo.catedra.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Horario semanal en JSON, como viene en el snapshot y en Redis.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatedraWeeklyScheduleResponse {

    private Long id;
    private Long professionalId;
    private String dayOfWeek;
    private String startTime;
    private String endTime;
    private Integer slotDurationMinutes;
    private Boolean enabled;
}
