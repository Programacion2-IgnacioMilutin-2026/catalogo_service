package catalogo.catedra.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// Respuesta del snapshot del catálogo.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogSnapshotResponse {

    private Long snapshotVersion;
    private List<SnapshotCategoryResponse> professionalCategories;
    private List<SnapshotProfessionalResponse> professionals;
    private List<SnapshotWeeklyScheduleResponse> weeklySchedules;
}
