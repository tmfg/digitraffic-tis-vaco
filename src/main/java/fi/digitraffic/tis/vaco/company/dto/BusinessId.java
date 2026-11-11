package fi.digitraffic.tis.vaco.company.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

/**
 * Requires a Finnish business ID in the format {@code 1234567-8} with a correct check digit.
 * A {@code null} value is valid. Add {@code @NotNull} to reject it.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = { BusinessId.Validator.class })
public @interface BusinessId {

    String message() default "must be in the format 1234567-8 with a valid check digit";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<BusinessId, String> {

        private static final Pattern FORMAT = Pattern.compile("\\d{7}-\\d");
        private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2};

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || isValidBusinessId(value);
        }

        static boolean isValidBusinessId(String businessId) {
            if (!FORMAT.matcher(businessId).matches()) {
                return false;
            }
            int sum = 0;
            for (int i = 0; i < WEIGHTS.length; i++) {
                sum += Character.digit(businessId.charAt(i), 10) * WEIGHTS[i];
            }
            int remainder = sum % 11;
            if (remainder == 1) {
                return false;
            }
            int expected = remainder == 0 ? 0 : 11 - remainder;
            return Character.digit(businessId.charAt(8), 10) == expected;
        }
    }
}
