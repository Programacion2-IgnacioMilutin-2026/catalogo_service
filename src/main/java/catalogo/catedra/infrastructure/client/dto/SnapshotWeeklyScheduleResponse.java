package catalogo.catedra.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Horario dentro de la respuesta del snapshot.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SnapshotWeeklyScheduleResponse {

    private Long id;
    private Long professionalId;
    private String dayOfWeek;
    private String startTime;
    private String endTime;
    private Integer slotDurationMinutes;
    private Boolean enabled;
}
