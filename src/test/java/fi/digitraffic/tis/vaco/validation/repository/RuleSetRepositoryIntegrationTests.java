package fi.digitraffic.tis.vaco.validation.repository;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.tis.repository.CooperationRepository;
import fi.digitraffic.tis.vaco.tis.repository.OrganizationRepository;
import fi.digitraffic.tis.vaco.validation.model.Category;
import fi.digitraffic.tis.vaco.tis.model.CooperationType;
import fi.digitraffic.tis.vaco.tis.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.tis.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class RuleSetRepositoryIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    CooperationRepository cooperationRepository;

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    RuleSetRepository rulesetRepository;

    @Test
    void rulesetsAreChosenBasedOnOwnership() {
        ImmutableOrganization fintraffic = organizationRepository.getByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID);
        ImmutableOrganization eskoOrg = organizationRepository.create(
                ImmutableOrganization.builder()
                        .name("Esko testaa")
                        .businessId("1234567-8")
                        .build());
        ImmutableOrganization tesmOrg = organizationRepository.create(
                ImmutableOrganization.builder()
                        .name("tesmaava Esko")
                        .businessId("8765432-1")
                        .build());

        ImmutableCooperation fintrafficAndEskoCoop = cooperationRepository.create(
                ImmutableCooperation.builder()
                        .cooperationType(CooperationType.AUTHORITY_PROVIDER)
                        .partnerA(fintraffic.id())
                        .partnerB(eskoOrg.id())
                        .build());
        ImmutableCooperation fintrafficAndTesmCoop = cooperationRepository.create(
                ImmutableCooperation.builder()
                        .cooperationType(CooperationType.AUTHORITY_PROVIDER)
                        .partnerA(fintraffic.id())
                        .partnerB(tesmOrg.id())
                        .build());

        ImmutableValidationRule ruleForAll = rulesetRepository.createRuleSet(
                ImmutableValidationRule.builder()
                        .ownerId(fintraffic.id())
                        .category(Category.GENERIC)
                        .identifyingName("all the rules")
                        .description("just testing")
                        .build());

        ImmutableValidationRule eskoOrgRule = rulesetRepository.createRuleSet(
                ImmutableValidationRule.builder()
                        .ownerId(eskoOrg.id())
                        .category(Category.SPECIFIC)
                        .identifyingName("just a rule")
                        .description("these are mine")
                        .build());

        assertThat(rulesetRepository.findRulesets(fintraffic.businessId()), equalTo(Set.of(ruleForAll)));
        assertThat(rulesetRepository.findRulesets(tesmOrg.businessId()), equalTo(Set.of(ruleForAll)));
        assertThat(rulesetRepository.findRulesets(eskoOrg.businessId()), equalTo(Set.of(ruleForAll, eskoOrgRule)));
    }
}
