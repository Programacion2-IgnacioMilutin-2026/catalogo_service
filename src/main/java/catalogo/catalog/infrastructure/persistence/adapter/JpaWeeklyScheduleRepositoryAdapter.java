package catalogo.catalog.infrastructure.persistence.adapter;

import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.domain.ports.out.WeeklyScheduleRepository;
import catalogo.catalog.infrastructure.persistence.mapper.WeeklyScheduleMapper;
import catalogo.catalog.infrastructure.persistence.repository.JpaWeeklyScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

// Guarda y quita horarios semanales con JPA.
@Component
@RequiredArgsConstructor
public class JpaWeeklyScheduleRepositoryAdapter implements WeeklyScheduleRepository {

    private final JpaWeeklyScheduleRepository jpaWeeklyScheduleRepository;
    private final WeeklyScheduleMapper weeklyScheduleMapper;

    @Override
    public void saveAll(List<WeeklySchedule> weeklySchedules) {
        jpaWeeklyScheduleRepository.saveAll(weeklySchedules.stream()
                .map(weeklyScheduleMapper::toEntity)
                .collect(Collectors.toList()));
    }

    @Override
    @Transactional
    public void deleteAllExcept(Collection<Long> ids) {
        if (ids.isEmpty()) {
            jpaWeeklyScheduleRepository.deleteAll();
        } else {
            jpaWeeklyScheduleRepository.deleteByIdNotIn(ids);
        }
    }
}
