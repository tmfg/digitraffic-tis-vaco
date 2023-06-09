package fi.digitraffic.tis.vaco.validation.repository;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.validation.model.Category;
import fi.digitraffic.tis.vaco.validation.model.CooperationType;
import fi.digitraffic.tis.vaco.validation.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.validation.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationRule;
import fi.digitraffic.tis.vaco.validation.model.ValidationRule;
import fi.digitraffic.tis.vaco.validation.rules.gtfs.CanonicalGtfsValidatorRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class RuleSetsRepositoryIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    OrganizationsRepository organizationsRepository;

    @Autowired
    RuleSetsRepository rulesetsRepository;

    @Test
    void hasDefaultRulesAlwaysAvailable() {
        ImmutableOrganization fintraffic = organizationsRepository.findByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID);
        ValidationRule canonicalGtfsValidator = rulesetsRepository.findByName(CanonicalGtfsValidatorRule.RULE_NAME).get();
        assertThat(rulesetsRepository.findRulesets(fintraffic.businessId()), equalTo(Set.of(canonicalGtfsValidator)));
    }

    @Test
    void rulesetsAreChosenBasedOnOwnership() {
        ImmutableOrganization fintraffic = organizationsRepository.findByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID);
        ImmutableOrganization eskoOrg = organizationsRepository.createOrganization(
                ImmutableOrganization.builder()
                        .name("Esko testaa")
                        .businessId("1234567-8")
                        .build());
        ImmutableOrganization tesmOrg = organizationsRepository.createOrganization(
                ImmutableOrganization.builder()
                        .name("tesmaava Esko")
                        .businessId("8765432-1")
                        .build());

        ImmutableCooperation fintrafficAndEskoCoop = organizationsRepository.createCooperation(
                ImmutableCooperation.builder()
                        .cooperationType(CooperationType.AUTHORITY_PROVIDER)
                        .partnerA(fintraffic.id())
                        .partnerB(eskoOrg.id())
                        .build());
        ImmutableCooperation fintrafficAndTesmCoop = organizationsRepository.createCooperation(
                ImmutableCooperation.builder()
                        .cooperationType(CooperationType.AUTHORITY_PROVIDER)
                        .partnerA(fintraffic.id())
                        .partnerB(tesmOrg.id())
                        .build());

        ValidationRule canonicalGtfsValidator = rulesetsRepository.findByName(CanonicalGtfsValidatorRule.RULE_NAME).get();

        ImmutableValidationRule eskoOrgRule = rulesetsRepository.createRuleSet(
                ImmutableValidationRule.builder()
                        .ownerId(eskoOrg.id())
                        .category(Category.SPECIFIC)
                        .identifyingName("just a rule")
                        .description("these are mine")
                        .build());

        assertThat(rulesetsRepository.findRulesets(fintraffic.businessId()), equalTo(Set.of(canonicalGtfsValidator)));
        assertThat(rulesetsRepository.findRulesets(tesmOrg.businessId()), equalTo(Set.of(canonicalGtfsValidator)));
        assertThat(rulesetsRepository.findRulesets(eskoOrg.businessId()), equalTo(Set.of(canonicalGtfsValidator, eskoOrgRule)));
    }
}
