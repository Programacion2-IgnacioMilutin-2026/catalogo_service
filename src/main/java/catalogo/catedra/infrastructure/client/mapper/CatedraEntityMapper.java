package catalogo.catedra.infrastructure.client.mapper;

import catalogo.catedra.domain.model.CatedraCategory;
import catalogo.catedra.domain.model.CatedraProfessional;
import catalogo.catedra.domain.model.CatedraWeeklySchedule;
import catalogo.catedra.infrastructure.client.dto.CatedraCategoryResponse;
import catalogo.catedra.infrastructure.client.dto.CatedraProfessionalResponse;
import catalogo.catedra.infrastructure.client.dto.CatedraWeeklyScheduleResponse;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

// Revisa que cada categoría, profesional y horario de la cátedra tenga los datos y formatos que se guardan y lo convierte al modelo.
@Component
public class CatedraEntityMapper {

    private static final Pattern LOCAL_TIME = Pattern.compile("\\d{2}:\\d{2}(:\\d{2})?");

    public List<String> problemsOf(CatedraCategoryResponse category, String where) {
        if (category == null) {
            return List.of(where + ": empty");
        }
        List<String> problems = new ArrayList<>();
        requirePresent(category.getId(), where, "id", problems);
        requireText(category.getName(), where, "name", problems);
        requirePresent(category.getEnabled(), where, "enabled", problems);
        return List.copyOf(problems);
    }

    public List<String> problemsOf(CatedraProfessionalResponse professional, String where) {
        if (professional == null) {
            return List.of(where + ": empty");
        }
        List<String> problems = new ArrayList<>();
        requirePresent(professional.getId(), where, "id", problems);
        requirePresent(professional.getCategoryId(), where, "categoryId", problems);
        requireText(professional.getFirstName(), where, "firstName", problems);
        requireText(professional.getLastName(), where, "lastName", problems);
        requirePresent(professional.getEnabled(), where, "enabled", problems);
        return List.copyOf(problems);
    }

    public List<String> problemsOf(CatedraWeeklyScheduleResponse schedule, String where) {
        if (schedule == null) {
            return List.of(where + ": empty");
        }
        List<String> problems = new ArrayList<>();
        requirePresent(schedule.getId(), where, "id", problems);
        requirePresent(schedule.getProfessionalId(), where, "professionalId", problems);
        if (!isDayOfWeek(schedule.getDayOfWeek())) {
            problems.add(where + ": invalid dayOfWeek");
        }
        requireLocalTime(schedule.getStartTime(), where, "startTime", problems);
        requireLocalTime(schedule.getEndTime(), where, "endTime", problems);
        requirePresent(schedule.getSlotDurationMinutes(), where, "slotDurationMinutes", problems);
        requirePresent(schedule.getEnabled(), where, "enabled", problems);
        return List.copyOf(problems);
    }

    public CatedraCategory toDomain(CatedraCategoryResponse category) {
        return new CatedraCategory(category.getId(), category.getName(), category.getDescription(),
                category.getEnabled());
    }

    public CatedraProfessional toDomain(CatedraProfessionalResponse professional) {
        return new CatedraProfessional(professional.getId(), professional.getCategoryId(),
                professional.getFirstName(), professional.getLastName(), professional.getEnabled());
    }

    public CatedraWeeklySchedule toDomain(CatedraWeeklyScheduleResponse schedule) {
        return new CatedraWeeklySchedule(schedule.getId(), schedule.getProfessionalId(),
                DayOfWeek.valueOf(schedule.getDayOfWeek()), LocalTime.parse(schedule.getStartTime()),
                LocalTime.parse(schedule.getEndTime()), schedule.getSlotDurationMinutes(), schedule.getEnabled());
    }

    private static void requirePresent(Object value, String where, String field, List<String> problems) {
        if (value == null) {
            problems.add(where + ": missing " + field);
        }
    }

    private static void requireText(String value, String where, String field, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(where + ": missing " + field);
        }
    }

    private static void requireLocalTime(String value, String where, String field, List<String> problems) {
        if (value == null || !LOCAL_TIME.matcher(value).matches() || !isLocalTime(value)) {
            problems.add(where + ": invalid " + field);
        }
    }

    private static boolean isLocalTime(String value) {
        try {
            LocalTime.parse(value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isDayOfWeek(String value) {
        return value != null && Arrays.stream(DayOfWeek.values()).anyMatch(day -> day.name().equals(value));
    }
}
