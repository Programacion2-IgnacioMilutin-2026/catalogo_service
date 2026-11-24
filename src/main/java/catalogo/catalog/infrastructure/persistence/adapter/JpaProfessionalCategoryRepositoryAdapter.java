package catalogo.catalog.infrastructure.persistence.adapter;

import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.ports.out.ProfessionalCategoryRepository;
import catalogo.catalog.infrastructure.persistence.mapper.ProfessionalCategoryMapper;
import catalogo.catalog.infrastructure.persistence.repository.JpaProfessionalCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

// Guarda y quita categorías con JPA.
@Component
@RequiredArgsConstructor
public class JpaProfessionalCategoryRepositoryAdapter implements ProfessionalCategoryRepository {

    private final JpaProfessionalCategoryRepository jpaProfessionalCategoryRepository;
    private final ProfessionalCategoryMapper professionalCategoryMapper;

    @Override
    public void saveAll(List<ProfessionalCategory> categories) {
        jpaProfessionalCategoryRepository.saveAll(categories.stream()
                .map(professionalCategoryMapper::toEntity)
                .collect(Collectors.toList()));
    }

    @Override
    @Transactional
    public void deleteAllExcept(Collection<Long> ids) {
        if (ids.isEmpty()) {
            jpaProfessionalCategoryRepository.deleteAll();
        } else {
            jpaProfessionalCategoryRepository.deleteByIdNotIn(ids);
        }
    }

    @Override
    public List<ProfessionalCategory> findAllByIds(Collection<Long> ids) {
        return jpaProfessionalCategoryRepository.findAllById(ids).stream()
                .map(professionalCategoryMapper::toDomainModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProfessionalCategory> findAllOrderedByName() {
        return jpaProfessionalCategoryRepository.findAllByOrderByNameAscIdAsc().stream()
                .map(professionalCategoryMapper::toDomainModel)
                .collect(Collectors.toList());
    }
}
