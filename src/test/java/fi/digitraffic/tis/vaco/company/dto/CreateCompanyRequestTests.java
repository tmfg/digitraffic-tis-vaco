package fi.digitraffic.tis.vaco.company.dto;

import fi.digitraffic.tis.vaco.company.model.Company;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

class CreateCompanyRequestTests {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> invalidFields(CreateCompanyRequest request) {
        return validator.validate(request).stream()
            .map(ConstraintViolation::getPropertyPath)
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2942108-7", "0000000-0", "1234567-1"})
    void acceptsBusinessIdWithCorrectCheckDigit(String businessId) {
        assertThat(invalidFields(new CreateCompanyRequest(businessId, "Oy Ab", true)), empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "2499374-9", "12345678", "123456-7", "abcdefg-h", " 2942108-7", ""})
    void rejectsBusinessIdWithBadFormatOrCheckDigit(String businessId) {
        assertThat(invalidFields(new CreateCompanyRequest(businessId, "Oy Ab", true)), contains("businessId"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void rejectsBusinessIdWhoseWeightedSumLeavesRemainderOne(int checkDigit) {
        // 0000030 has the weighted sum 3 * 4 = 12, and no check digit is valid when the sum modulo 11 is 1
        assertThat(invalidFields(new CreateCompanyRequest("0000030-" + checkDigit, "Oy Ab", true)), contains("businessId"));
    }

    @Test
    void rejectsMissingBusinessId() {
        assertThat(invalidFields(new CreateCompanyRequest(null, "Oy Ab", true)), contains("businessId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsBlankName(String name) {
        assertThat(invalidFields(new CreateCompanyRequest("2942108-7", name, true)), contains("name"));
    }

    @Test
    void rejectsMissingName() {
        assertThat(invalidFields(new CreateCompanyRequest("2942108-7", null, true)), contains("name"));
    }

    @Test
    void convertsToCompanyWithTheGivenValues() {
        Company company = new CreateCompanyRequest("2942108-7", "Fintraffic Oy", false).toCompany();

        assertThat(company.businessId(), equalTo("2942108-7"));
        assertThat(company.name(), equalTo("Fintraffic Oy"));
        assertThat(company.publish(), equalTo(false));
    }
}
