package catalogo.catalog.infrastructure.persistence.mapper;

import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.infrastructure.persistence.entity.WeeklyScheduleEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Convierte un horario semanal entre el dominio y la entidad.
@Component
@RequiredArgsConstructor
public class WeeklyScheduleMapper {

    private final ProfessionalMapper professionalMapper;

    public WeeklyScheduleEntity toEntity(WeeklySchedule weeklySchedule) {
        if (weeklySchedule == null) {
            return null;
        }
        return WeeklyScheduleEntity.builder()
                .id(weeklySchedule.getId())
                .dayOfWeek(weeklySchedule.getDayOfWeek())
                .startTime(weeklySchedule.getStartTime())
                .endTime(weeklySchedule.getEndTime())
                .slotDurationMinutes(weeklySchedule.getSlotDurationMinutes())
                .enabled(weeklySchedule.isEnabled())
                .professional(professionalMapper.toEntity(weeklySchedule.getProfessional()))
                .build();
    }

    public WeeklySchedule toDomainModel(WeeklyScheduleEntity entity) {
        if (entity == null) {
            return null;
        }
        return WeeklySchedule.builder()
                .id(entity.getId())
                .dayOfWeek(entity.getDayOfWeek())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .slotDurationMinutes(entity.getSlotDurationMinutes())
                .enabled(entity.isEnabled())
                .professional(professionalMapper.toDomainModel(entity.getProfessional()))
                .build();
    }
}
