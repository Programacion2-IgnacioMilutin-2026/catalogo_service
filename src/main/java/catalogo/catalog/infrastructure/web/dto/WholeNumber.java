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
import java.math.BigInteger;

// Un número entero dentro de un rango.
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WholeNumber.Validator.class)
public @interface WholeNumber {

    long min();

    long max();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<WholeNumber, String> {

        private BigInteger min;
        private BigInteger max;

        @Override
        public void initialize(WholeNumber annotation) {
            min = BigInteger.valueOf(annotation.min());
            max = BigInteger.valueOf(annotation.max());
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isEmpty()) {
                return true;
            }
            if (!value.matches("-?\\d+")) {
                return false;
            }
            BigInteger number = new BigInteger(value);
            return number.compareTo(min) >= 0 && number.compareTo(max) <= 0;
        }
    }
}
