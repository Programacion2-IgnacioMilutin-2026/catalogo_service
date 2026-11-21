package catalogo.catalog.application.usecases;

import catalogo.catalog.application.exception.CatalogNotInitializedException;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.ports.in.GetAllProfessionalCategoriesUseCase;
import catalogo.catalog.domain.ports.out.CatalogSyncStateRepository;
import catalogo.catalog.domain.ports.out.ProfessionalCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// Devuelve todas las categorías ordenadas.
@Component
@RequiredArgsConstructor
public class GetAllProfessionalCategoriesUseCaseImpl implements GetAllProfessionalCategoriesUseCase {

    private final CatalogSyncStateRepository catalogSyncStateRepository;
    private final ProfessionalCategoryRepository professionalCategoryRepository;

    @Override
    public List<ProfessionalCategory> getAllProfessionalCategories() {
        if (!catalogSyncStateRepository.get().isInitialized()) {
            throw new CatalogNotInitializedException();
        }
        return professionalCategoryRepository.findAllOrderedByName();
    }
}
