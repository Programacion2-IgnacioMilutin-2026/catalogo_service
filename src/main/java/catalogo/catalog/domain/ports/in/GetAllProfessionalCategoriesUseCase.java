package catalogo.catalog.domain.ports.in;

import catalogo.catalog.domain.model.ProfessionalCategory;

import java.util.List;

// Puerto de entrada para listar todas las categorías.
public interface GetAllProfessionalCategoriesUseCase {

    List<ProfessionalCategory> getAllProfessionalCategories();
}
