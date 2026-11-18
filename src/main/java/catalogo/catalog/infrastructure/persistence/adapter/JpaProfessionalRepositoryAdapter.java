package catalogo.catalog.infrastructure.persistence.adapter;

import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import catalogo.catalog.infrastructure.persistence.mapper.ProfessionalMapper;
import catalogo.catalog.infrastructure.persistence.repository.JpaProfessionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

// Guarda y quita profesionales con JPA.
@Component
@RequiredArgsConstructor
public class JpaProfessionalRepositoryAdapter implements ProfessionalRepository {

    private final JpaProfessionalRepository jpaProfessionalRepository;
    private final ProfessionalMapper professionalMapper;

    @Override
    public void saveAll(List<Professional> professionals) {
        jpaProfessionalRepository.saveAll(professionals.stream()
                .map(professionalMapper::toEntity)
                .collect(Collectors.toList()));
    }

    @Override
    @Transactional
    public void deleteAllExcept(Collection<Long> ids) {
        if (ids.isEmpty()) {
            jpaProfessionalRepository.deleteAll();
        } else {
            jpaProfessionalRepository.deleteByIdNotIn(ids);
        }
    }
}
