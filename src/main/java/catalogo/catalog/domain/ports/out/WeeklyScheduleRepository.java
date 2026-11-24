package catalogo.catalog.domain.ports.out;

import catalogo.catalog.domain.model.WeeklySchedule;

import java.util.Collection;
import java.util.List;

// Puerto de salida para utilizar repositorio de horario semanal.
public interface WeeklyScheduleRepository {

    void saveAll(List<WeeklySchedule> weeklySchedules);

    void deleteAllExcept(Collection<Long> ids);

    List<WeeklySchedule> findAllByProfessionalIds(Collection<Long> professionalIds);
}
