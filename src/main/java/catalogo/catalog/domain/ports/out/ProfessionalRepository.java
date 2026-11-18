package catalogo.catalog.domain.ports.out;

import catalogo.catalog.domain.model.Professional;

import java.util.Collection;
import java.util.List;

// Puerto de salida para utilizar repositorio de profesional.
public interface ProfessionalRepository {

    void saveAll(List<Professional> professionals);

    void deleteAllExcept(Collection<Long> ids);

    List<Professional> findAllByIds(Collection<Long> ids);
}
