package catalogo.catalog.domain.ports.in;

import catalogo.catalog.domain.model.ProfessionalWithSchedules;

import java.util.Optional;

// Puerto de entrada de la consulta de un profesional con sus horarios.
public interface GetProfessionalUseCase {

    Optional<ProfessionalWithSchedules> getProfessional(Long professionalId);
}
