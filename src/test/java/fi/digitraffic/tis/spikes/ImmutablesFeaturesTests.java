package fi.digitraffic.tis.spikes;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.immutables.value.Value;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class ImmutablesFeaturesTests {

    /**
     * @see <a href="https://stackoverflow.com/questions/2781771/how-can-i-validate-two-or-more-fields-in-combination">StackOverflow.com: How can I validate two or more fields in combination?</a>
     */
    @Test
    void typeLevelJakartaAnnotationCloning() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ImmutableMultiFieldValidation valid = ImmutableMultiFieldValidation.builder()
                .fieldA("A")
                .fieldB("B")
                .build();
        Set<ConstraintViolation<ImmutableMultiFieldValidation>> validResult = validator.validate(valid);
        assertThat(validResult, empty());

        ImmutableMultiFieldValidation invalid = ImmutableMultiFieldValidation.builder()
                .fieldA("C")
                .fieldB("C")
                .build();
        Set<ConstraintViolation<ImmutableMultiFieldValidation>> invalidResult = validator.validate(invalid);
        assertThat(invalidResult, hasSize(1));
        ConstraintViolation<ImmutableMultiFieldValidation> err = invalidResult.stream().findFirst().get();
        assertThat(err.getMessage(), equalTo("{fi.digitraffic.tis.constraint.ExampleMessage}"));
    }

    @Value.Immutable
    @UniqueFields // <- custom multifield validation annotation, works as is
    interface MultiFieldValidation {
        String fieldA();
        String fieldB();
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = { UniqueFields.Validator.class }) // <-- see javadoc for explanation of required fields
    public @interface UniqueFields {

        // message is required by Jakarta Validation, the curly syntax is a property lookup
        String message() default "{fi.digitraffic.tis.constraint.ExampleMessage}";

        // groups is required by Jakarta Validation, empty is fine
        Class<?>[] groups() default {};

        // payload is required by Jakarta Validation, empty is fine
        Class<? extends Payload>[] payload() default {};

        class Validator implements ConstraintValidator<UniqueFields, MultiFieldValidation> {
            @Override
            public boolean isValid(MultiFieldValidation value, ConstraintValidatorContext context) {
                return !value.fieldA().equals(value.fieldB());
            }
        }
    }
}
