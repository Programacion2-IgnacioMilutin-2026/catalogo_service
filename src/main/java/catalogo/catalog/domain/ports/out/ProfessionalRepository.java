package catalogo.catalog.domain.ports.out;

import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// Puerto de salida para utilizar repositorio de profesional.
public interface ProfessionalRepository {

    void saveAll(List<Professional> professionals);

    void deleteAllExcept(Collection<Long> ids);

    List<Professional> findAllByIds(Collection<Long> ids);

    Optional<Professional> findById(Long id);

    long countMatching(ProfessionalSearchCriteria criteria);

    List<Professional> findMatching(ProfessionalSearchCriteria criteria, int page, int size);
}
