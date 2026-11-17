package catalogo.catedra.infrastructure.client.mapper;

import catalogo.catedra.domain.model.CatalogSnapshot;
import catalogo.catedra.domain.model.SnapshotCategory;
import catalogo.catedra.domain.model.SnapshotProfessional;
import catalogo.catedra.domain.model.SnapshotWeeklySchedule;
import catalogo.catedra.infrastructure.client.dto.CatalogSnapshotResponse;
import catalogo.catedra.infrastructure.client.dto.SnapshotCategoryResponse;
import catalogo.catedra.infrastructure.client.dto.SnapshotProfessionalResponse;
import catalogo.catedra.infrastructure.client.dto.SnapshotWeeklyScheduleResponse;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

// Revisa que la respuesta del snapshot tenga los datos y formatos que se guardan, y la convierte al modelo de dominio.
@Component
public class CatalogSnapshotMapper {

    private static final Pattern LOCAL_TIME = Pattern.compile("\\d{2}:\\d{2}(:\\d{2})?");

    public List<String> problemsOf(CatalogSnapshotResponse response) {
        List<String> problems = new ArrayList<>();
        if (response == null) {
            return List.of("empty response");
        }
        if (response.getSnapshotVersion() == null) {
            problems.add("missing snapshotVersion");
        }
        checkCategories(response.getProfessionalCategories(), problems);
        checkProfessionals(response.getProfessionals(), problems);
        checkWeeklySchedules(response.getWeeklySchedules(), problems);
        return List.copyOf(problems);
    }

    // Solo para una respuesta sin problemas (problemsOf vacío).
    public CatalogSnapshot toDomain(CatalogSnapshotResponse response) {
        return new CatalogSnapshot(
                response.getSnapshotVersion(),
                response.getProfessionalCategories().stream()
                        .map(c -> new SnapshotCategory(c.getId(), c.getName(), c.getDescription(), c.getEnabled()))
                        .toList(),
                response.getProfessionals().stream()
                        .map(p -> new SnapshotProfessional(p.getId(), p.getCategoryId(), p.getFirstName(),
                                p.getLastName(), p.getEnabled()))
                        .toList(),
                response.getWeeklySchedules().stream()
                        .map(w -> new SnapshotWeeklySchedule(w.getId(), w.getProfessionalId(),
                                DayOfWeek.valueOf(w.getDayOfWeek()), LocalTime.parse(w.getStartTime()),
                                LocalTime.parse(w.getEndTime()), w.getSlotDurationMinutes(), w.getEnabled()))
                        .toList());
    }

    private static void checkCategories(List<SnapshotCategoryResponse> categories, List<String> problems) {
        if (categories == null) {
            problems.add("missing professionalCategories");
            return;
        }
        for (int i = 0; i < categories.size(); i++) {
            SnapshotCategoryResponse category = categories.get(i);
            String where = "professionalCategories[" + i + "]";
            if (category == null) {
                problems.add(where + ": empty");
                continue;
            }
            requirePresent(category.getId(), where, "id", problems);
            requireText(category.getName(), where, "name", problems);
            requirePresent(category.getEnabled(), where, "enabled", problems);
        }
    }

    private static void checkProfessionals(List<SnapshotProfessionalResponse> professionals, List<String> problems) {
        if (professionals == null) {
            problems.add("missing professionals");
            return;
        }
        for (int i = 0; i < professionals.size(); i++) {
            SnapshotProfessionalResponse professional = professionals.get(i);
            String where = "professionals[" + i + "]";
            if (professional == null) {
                problems.add(where + ": empty");
                continue;
            }
            requirePresent(professional.getId(), where, "id", problems);
            requirePresent(professional.getCategoryId(), where, "categoryId", problems);
            requireText(professional.getFirstName(), where, "firstName", problems);
            requireText(professional.getLastName(), where, "lastName", problems);
            requirePresent(professional.getEnabled(), where, "enabled", problems);
        }
    }

    private static void checkWeeklySchedules(List<SnapshotWeeklyScheduleResponse> schedules, List<String> problems) {
        if (schedules == null) {
            problems.add("missing weeklySchedules");
            return;
        }
        for (int i = 0; i < schedules.size(); i++) {
            SnapshotWeeklyScheduleResponse schedule = schedules.get(i);
            String where = "weeklySchedules[" + i + "]";
            if (schedule == null) {
                problems.add(where + ": empty");
                continue;
            }
            requirePresent(schedule.getId(), where, "id", problems);
            requirePresent(schedule.getProfessionalId(), where, "professionalId", problems);
            if (!isDayOfWeek(schedule.getDayOfWeek())) {
                problems.add(where + ": invalid dayOfWeek");
            }
            requireLocalTime(schedule.getStartTime(), where, "startTime", problems);
            requireLocalTime(schedule.getEndTime(), where, "endTime", problems);
            requirePresent(schedule.getSlotDurationMinutes(), where, "slotDurationMinutes", problems);
            requirePresent(schedule.getEnabled(), where, "enabled", problems);
        }
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
