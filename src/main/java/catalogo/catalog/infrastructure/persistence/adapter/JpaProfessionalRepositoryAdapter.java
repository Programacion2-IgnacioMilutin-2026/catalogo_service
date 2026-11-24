package catalogo.catalog.infrastructure.persistence.adapter;

import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;
import catalogo.catalog.domain.ports.out.ProfessionalRepository;
import catalogo.catalog.infrastructure.persistence.entity.ProfessionalEntity;
import catalogo.catalog.infrastructure.persistence.entity.WeeklyScheduleEntity;
import catalogo.catalog.infrastructure.persistence.mapper.ProfessionalMapper;
import catalogo.catalog.infrastructure.persistence.repository.JpaProfessionalRepository;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    public Optional<Professional> findById(Long id) {
        return jpaProfessionalRepository.findById(id).map(professionalMapper::toDomainModel);
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
        List<Specification<ProfessionalEntity>> conditions = new ArrayList<>();
        if (criteria.getCategoryId() != null) {
            conditions.add(inCategory(criteria.getCategoryId()));
        }
        if (criteria.getEnabled() != null) {
            conditions.add(withEnabled(criteria.getEnabled()));
        }
        criteria.nameWords().forEach(word -> conditions.add(nameContains(word)));
        if (hasAvailability(criteria)) {
            conditions.add(availableIn(criteria.getDaysOfWeek(), criteria.getFrom(), criteria.getTo()));
        }
        return Specification.allOf(conditions);
    }

    private static boolean hasAvailability(ProfessionalSearchCriteria criteria) {
        return (criteria.getDaysOfWeek() != null && !criteria.getDaysOfWeek().isEmpty())
                || criteria.getFrom() != null || criteria.getTo() != null;
    }

    private static Specification<ProfessionalEntity> availableIn(Set<DayOfWeek> days, LocalTime from, LocalTime to) {
        return (root, query, builder) -> {
            Subquery<Long> schedules = query.subquery(Long.class);
            Root<WeeklyScheduleEntity> schedule = schedules.from(WeeklyScheduleEntity.class);
            Path<LocalTime> start = schedule.get("startTime");
            Path<LocalTime> end = schedule.get("endTime");
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(builder.equal(schedule.get("professional").get("id"), root.get("id")));
            conditions.add(builder.isTrue(schedule.get("enabled")));
            conditions.add(builder.lessThan(start, end));
            if (days != null && !days.isEmpty()) {
                conditions.add(schedule.get("dayOfWeek").in(days));
            }
            if (from != null) {
                conditions.add(builder.greaterThan(end, from));
            }
            if (to != null) {
                conditions.add(builder.lessThan(start, to));
            }
            return builder.exists(schedules.select(schedule.get("id")).where(conditions.toArray(Predicate[]::new)));
        };
    }

    private static Specification<ProfessionalEntity> inCategory(Long categoryId) {
        return (root, query, builder) -> builder.equal(root.get("category").get("id"), categoryId);
    }

    private static Specification<ProfessionalEntity> withEnabled(boolean enabled) {
        return (root, query, builder) -> builder.equal(root.get("enabled"), enabled);
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
