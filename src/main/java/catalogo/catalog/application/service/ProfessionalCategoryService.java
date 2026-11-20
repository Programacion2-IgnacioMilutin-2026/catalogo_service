package catalogo.catalog.application.service;

import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.ports.in.GetAllProfessionalCategoriesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// Servicio que brinda los casos de uso de las categorías de profesionales.
@Service
@RequiredArgsConstructor
public class ProfessionalCategoryService {

    private final GetAllProfessionalCategoriesUseCase getAllProfessionalCategoriesUseCase;

    public List<ProfessionalCategory> getAllProfessionalCategories() {
        return getAllProfessionalCategoriesUseCase.getAllProfessionalCategories();
    }
}
