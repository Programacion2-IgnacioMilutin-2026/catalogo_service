package catalogo.catalog.application.service;

import catalogo.catalog.application.exception.ProfessionalNotFoundException;
import catalogo.catalog.domain.model.ProfessionalPage;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;
import catalogo.catalog.domain.model.ProfessionalWithSchedules;
import catalogo.catalog.domain.ports.in.GetProfessionalUseCase;
import catalogo.catalog.domain.ports.in.SearchProfessionalsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Servicio que brinda los casos de uso de los profesionales.
@Service
@RequiredArgsConstructor
public class ProfessionalService {

    private final SearchProfessionalsUseCase searchProfessionalsUseCase;
    private final GetProfessionalUseCase getProfessionalUseCase;

    public ProfessionalPage searchProfessionals(ProfessionalSearchCriteria criteria, int page, int size) {
        return searchProfessionalsUseCase.searchProfessionals(criteria, page, size);
    }

    public ProfessionalWithSchedules getProfessional(Long professionalId) {
        return getProfessionalUseCase.getProfessional(professionalId)
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));
    }
}
