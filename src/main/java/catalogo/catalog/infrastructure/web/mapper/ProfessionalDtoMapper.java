package catalogo.catalog.infrastructure.web.mapper;

import catalogo.catalog.domain.model.Professional;
import catalogo.catalog.domain.model.ProfessionalCategory;
import catalogo.catalog.domain.model.ProfessionalPage;
import catalogo.catalog.domain.model.ProfessionalSearchCriteria;
import catalogo.catalog.domain.model.ProfessionalWithSchedules;
import catalogo.catalog.domain.model.WeeklySchedule;
import catalogo.catalog.infrastructure.web.dto.ProfessionalCategorySummaryResponse;
import catalogo.catalog.infrastructure.web.dto.ProfessionalPageResponse;
import catalogo.catalog.infrastructure.web.dto.ProfessionalResponse;
import catalogo.catalog.infrastructure.web.dto.ProfessionalSearchRequest;
import catalogo.catalog.infrastructure.web.dto.WeeklyScheduleResponse;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// Convierte la búsqueda de la app en filtros del dominio, y la página del dominio en la respuesta.
@Component
public class ProfessionalDtoMapper {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;

    public ProfessionalSearchCriteria toDomain(ProfessionalSearchRequest request) {
        if (request == null) {
            return null;
        }
        return ProfessionalSearchCriteria.builder()
                .categoryId(isEmpty(request.getCategoryId()) ? null : Long.valueOf(request.getCategoryId()))
                .enabled(isEmpty(request.getEnabled()) ? null : Boolean.valueOf(request.getEnabled()))
                .name(request.getName() == null || request.getName().isBlank() ? null : request.getName().strip())
                .daysOfWeek(daysOf(request.getDayOfWeek()))
                .from(isEmpty(request.getFrom()) ? null : LocalTime.parse(request.getFrom()))
                .to(isEmpty(request.getTo()) ? null : LocalTime.parse(request.getTo()))
                .build();
    }

    public int toPage(ProfessionalSearchRequest request) {
        return isEmpty(request.getPage()) ? DEFAULT_PAGE : Integer.parseInt(request.getPage());
    }

    public int toSize(ProfessionalSearchRequest request) {
        return isEmpty(request.getSize()) ? DEFAULT_SIZE : Integer.parseInt(request.getSize());
    }

    public ProfessionalPageResponse toResponse(ProfessionalPage professionalPage, int page, int size) {
        if (professionalPage == null) {
            return null;
        }
        return ProfessionalPageResponse.builder()
                .items(professionalPage.getProfessionals().stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList()))
                .page(page)
                .size(size)
                .totalItems(professionalPage.getTotalItems())
                .build();
    }

    private ProfessionalResponse toResponse(ProfessionalWithSchedules item) {
        Professional professional = item.getProfessional();
        return ProfessionalResponse.builder()
                .id(professional.getId())
                .firstName(professional.getFirstName())
                .lastName(professional.getLastName())
                .enabled(professional.isEnabled())
                .category(toResponse(professional.getCategory()))
                .weeklySchedules(item.getWeeklySchedules().stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    private ProfessionalCategorySummaryResponse toResponse(ProfessionalCategory category) {
        return ProfessionalCategorySummaryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .enabled(category.isEnabled())
                .build();
    }

    private WeeklyScheduleResponse toResponse(WeeklySchedule schedule) {
        return WeeklyScheduleResponse.builder()
                .id(schedule.getId())
                .dayOfWeek(schedule.getDayOfWeek())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .slotDurationMinutes(schedule.getSlotDurationMinutes())
                .enabled(schedule.isEnabled())
                .build();
    }

    private static Set<DayOfWeek> daysOf(List<String> values) {
        if (values == null) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        return values.stream()
                .filter(value -> !isEmpty(value))
                .map(value -> DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
