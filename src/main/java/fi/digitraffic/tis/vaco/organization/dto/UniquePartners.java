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
    // TODO: move to a proper centralized place for error messages
    String message() default "Provided partners' business ID are the same";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<UniquePartners, CooperationRequest> {
        @Override
        public boolean isValid(CooperationRequest value, ConstraintValidatorContext context) {
            return !value.partnerABusinessId().equals(value.partnerBBusinessId());
        }
    }
}
