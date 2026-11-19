package catalogo.catedra.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Profesional en JSON, como viene en el snapshot y en Redis.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatedraProfessionalResponse {

    private Long id;
    private Long categoryId;
    private String firstName;
    private String lastName;
    private Boolean enabled;
}
