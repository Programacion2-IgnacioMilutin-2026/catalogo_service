package catalogo.catalog.domain.ports.out;

import catalogo.catalog.domain.model.ProfessionalCategory;

import java.util.Collection;
import java.util.List;

// Puerto de salida para utilizar repositorio de categoria de profesional.
public interface ProfessionalCategoryRepository {

    void saveAll(List<ProfessionalCategory> categories);

    void deleteAllExcept(Collection<Long> ids);

    // Las que existen entre esos ids; los que no están, no vienen.
    List<ProfessionalCategory> findAllByIds(Collection<Long> ids);
}
