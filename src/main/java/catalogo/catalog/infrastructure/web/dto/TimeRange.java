package catalogo.catalog.infrastructure.web.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalTime;

// La franja de la búsqueda: "from" tiene que ser anterior a "to".
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimeRange.Validator.class)
public @interface TimeRange {

    String message() default "must be before to";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<TimeRange, ProfessionalSearchRequest> {

        @Override
        public boolean isValid(ProfessionalSearchRequest request, ConstraintValidatorContext context) {
            LocalTime from = readable(request.getFrom());
            LocalTime to = readable(request.getTo());
            if (from == null || to == null || from.isBefore(to)) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("from")
                    .addConstraintViolation();
            return false;
        }

        private static LocalTime readable(String value) {
            if (value == null || value.isEmpty() || !value.matches(ProfessionalSearchRequest.TIME_FORMAT)) {
                return null;
            }
            return LocalTime.parse(value);
        }
    }
}
