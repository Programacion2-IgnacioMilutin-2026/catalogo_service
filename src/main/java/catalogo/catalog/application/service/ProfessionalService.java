package catalogo.catalog.application.service;

import catalogo.catalog.domain.model.ProfessionalPage;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;
import catalogo.catalog.domain.ports.in.SearchProfessionalsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Servicio que brinda los casos de uso de los profesionales.
@Service
@RequiredArgsConstructor
public class ProfessionalService {

    private final SearchProfessionalsUseCase searchProfessionalsUseCase;

    public ProfessionalPage searchProfessionals(ProfessionalSearchCriteria criteria, int page, int size) {
        return searchProfessionalsUseCase.searchProfessionals(criteria, page, size);
    }
}
