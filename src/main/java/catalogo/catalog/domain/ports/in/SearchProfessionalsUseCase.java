package catalogo.catalog.domain.ports.in;

import catalogo.catalog.domain.model.ProfessionalPage;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;

// Puerto de entrada de la búsqueda de profesionales.
public interface SearchProfessionalsUseCase {

    ProfessionalPage searchProfessionals(ProfessionalSearchCriteria criteria, int page, int size);
}
