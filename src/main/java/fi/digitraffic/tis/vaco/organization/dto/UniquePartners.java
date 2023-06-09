package fi.digitraffic.tis.vaco.organization.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = { UniquePartners.Validator.class })
public @interface UniquePartners {
    // message is required by Jakarta Validation, the curly syntax is a property lookup
    String message() default "{fi.digitraffic.tis.constraint.ExampleMessage}";

    // groups is required by Jakarta Validation, empty is fine
    Class<?>[] groups() default {};

    // payload is required by Jakarta Validation, empty is fine
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<UniquePartners, CooperationCommand> {
        @Override
        public boolean isValid(CooperationCommand value, ConstraintValidatorContext context) {
            return !value.partnerABusinessId().equals(value.partnerBBusinessId());
        }
    }
}
