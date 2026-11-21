package catalogo.catalog.infrastructure.persistence.adapter;

import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import catalogo.catalog.infrastructure.persistence.entity.ProfessionalEntity;
import catalogo.catalog.infrastructure.persistence.mapper.ProfessionalMapper;
import catalogo.catalog.infrastructure.persistence.repository.JpaProfessionalRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

// Guarda, quita y busca profesionales con JPA.
@Component
@RequiredArgsConstructor
public class JpaProfessionalRepositoryAdapter implements ProfessionalRepository {

    private static final String SEARCH_COLLATION = "search_insensitive";
    private static final char LIKE_ESCAPE = '\\';
    private static final Sort SEARCH_ORDER = Sort.by("lastName", "firstName", "id");

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

    @Override
    public List<Professional> findAllByIds(Collection<Long> ids) {
        return jpaProfessionalRepository.findAllById(ids).stream()
                .map(professionalMapper::toDomainModel)
                .collect(Collectors.toList());
    }

    @Override
    public long countMatching(ProfessionalSearchCriteria criteria) {
        return jpaProfessionalRepository.count(matching(criteria));
    }

    @Override
    public List<Professional> findMatching(ProfessionalSearchCriteria criteria, int page, int size) {
        return jpaProfessionalRepository.findAll(matching(criteria), PageRequest.of(page, size, SEARCH_ORDER))
                .getContent().stream()
                .map(professionalMapper::toDomainModel)
                .collect(Collectors.toList());
    }

    private Specification<ProfessionalEntity> matching(ProfessionalSearchCriteria criteria) {
        return Specification.allOf(criteria.nameWords().stream()
                .map(JpaProfessionalRepositoryAdapter::nameContains)
                .toList());
    }

    private static Specification<ProfessionalEntity> nameContains(String word) {
        String pattern = "%" + escapeWildcards(word) + "%";
        return (root, query, builder) -> {
            HibernateCriteriaBuilder hibernate = (HibernateCriteriaBuilder) builder;
            return hibernate.or(
                    hibernate.like(hibernate.collate(root.<String>get("firstName"), SEARCH_COLLATION), pattern, LIKE_ESCAPE),
                    hibernate.like(hibernate.collate(root.<String>get("lastName"), SEARCH_COLLATION), pattern, LIKE_ESCAPE));
        };
    }

    private static String escapeWildcards(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
