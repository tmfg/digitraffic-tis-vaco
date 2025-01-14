package fi.digitraffic.tis.vaco.company.dto;

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

    String message() default "Provided partners' business ID are the same";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<UniquePartners, PartnershipRequest> {
        @Override
        public boolean isValid(PartnershipRequest value, ConstraintValidatorContext context) {
            return !value.partnerABusinessId().equals(value.partnerBBusinessId());
        }
    }
}
