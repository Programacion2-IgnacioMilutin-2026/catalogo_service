package catalogo.catalog.infrastructure.web.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// Los parámetros de la búsqueda tal como llegan en la URL.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TimeRange
public class ProfessionalSearchRequest {

    public static final String TIME_FORMAT = "|([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?";

    @WholeNumber(min = Long.MIN_VALUE, max = Long.MAX_VALUE, message = "must be a whole number")
    private String categoryId;

    private String name;

    @Pattern(regexp = "|true|false", message = "must be true or false")
    private String enabled;

    private List<@Pattern(regexp = "(?i)|MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY",
            message = "must be a day from MONDAY to SUNDAY") String> dayOfWeek;

    @Pattern(regexp = TIME_FORMAT, message = "must be a time as HH:mm or HH:mm:ss")
    private String from;

    @Pattern(regexp = TIME_FORMAT, message = "must be a time as HH:mm or HH:mm:ss")
    private String to;

    @WholeNumber(min = 0, max = Integer.MAX_VALUE, message = "must be a whole number from 0")
    private String page;

    @WholeNumber(min = 1, max = 100, message = "must be a whole number from 1 to 100")
    private String size;
}
