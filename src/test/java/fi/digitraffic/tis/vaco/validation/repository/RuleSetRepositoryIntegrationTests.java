package fi.digitraffic.tis.vaco.validation.repository;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.repository.CooperationRepository;
import fi.digitraffic.tis.vaco.organization.repository.OrganizationRepository;
import fi.digitraffic.tis.vaco.validation.model.Category;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationRule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import fi.digitraffic.tis.vaco.validation.model.ValidationRule;
import fi.digitraffic.tis.vaco.validation.rules.gtfs.CanonicalGtfsValidatorRule;
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

    private ImmutableOrganization fintraffic;
    private ImmutableOrganization parentOrg;
    private ImmutableOrganization currentOrg;
    private ImmutableOrganization otherOrg;
    private ImmutableValidationRule parentRuleA;
    private ImmutableValidationRule parentRuleB;
    private ImmutableValidationRule currentRuleC;
    private ImmutableValidationRule currentRuleD;
    private ImmutableValidationRule otherRuleE;

    @BeforeEach
    void setUp() {
        fintraffic = organizationRepository.findByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID).get();
        parentOrg = organizationRepository.create(
                ImmutableOrganization.builder()
                        .name("test parent")
                        .businessId("4433221-1")
                        .build());
        currentOrg = organizationRepository.create(
                ImmutableOrganization.builder()
                        .name("Esko testaa")
                        .businessId("1234567-8")
                        .build());
        otherOrg = organizationRepository.create(
                ImmutableOrganization.builder()
                        .name("tesmaava Esko")
                        .businessId("8765432-1")
                        .build());
        cooperationRepository.create(partnership(parentOrg, currentOrg));
        cooperationRepository.create(partnership(parentOrg, otherOrg));

        parentRuleA = rulesetRepository.createRuleSet(
                ImmutableValidationRule.of(parentOrg.id(), "GENERIC_A", "GENERIC_A", Category.GENERIC));
        parentRuleB = rulesetRepository.createRuleSet(
                ImmutableValidationRule.of(parentOrg.id(), "SPECIFIC_B", "SPECIFIC_B", Category.SPECIFIC));
        currentRuleC = rulesetRepository.createRuleSet(
                ImmutableValidationRule.of(currentOrg.id(), "SPECIFIC_C", "SPECIFIC_C", Category.SPECIFIC));
        currentRuleD = rulesetRepository.createRuleSet(
                ImmutableValidationRule.of(currentOrg.id(), "SPECIFIC_D", "SPECIFIC_D", Category.SPECIFIC));
        otherRuleE = rulesetRepository.createRuleSet(
                ImmutableValidationRule.of(otherOrg.id(), "SPECIFIC_E", "SPECIFIC_E", Category.SPECIFIC));
    }

    @AfterEach
    void tearDown() {
        organizationRepository.delete(parentOrg.businessId());
        organizationRepository.delete(currentOrg.businessId());
        organizationRepository.delete(otherOrg.businessId());
        rulesetRepository.deleteRuleSet(parentRuleA);
        rulesetRepository.deleteRuleSet(parentRuleB);
        rulesetRepository.deleteRuleSet(currentRuleC);
        rulesetRepository.deleteRuleSet(currentRuleD);
        rulesetRepository.deleteRuleSet(otherRuleE);
    }

    /**
     * Everything under Fintraffic will always get default rules. See `R__seed_data.sql` in DB Migrator repository.
     */
    @Test
    void hasDefaultRulesAlwaysAvailable() {
        ValidationRule canonicalGtfsValidator = rulesetRepository.findByName(CanonicalGtfsValidatorRule.RULE_NAME).get();
        assertThat(rulesetRepository.findRulesets(fintraffic.businessId()), equalTo(Set.of(canonicalGtfsValidator)));
    }

    @Test
    void rulesetsAreChosenBasedOnOwnership() {
        assertThat(rulesetRepository.findRulesets(parentOrg.businessId()), equalTo(Set.of(parentRuleA, parentRuleB)));
        assertThat(rulesetRepository.findRulesets(otherOrg.businessId()), equalTo(Set.of(parentRuleA, otherRuleE)));
        assertThat(rulesetRepository.findRulesets(currentOrg.businessId()), equalTo(Set.of(parentRuleA, currentRuleC, currentRuleD)));
    }

    /**
     * @see <a href="https://finrail.atlassian.net/browse/TIS-79">TIS-79</a>
     */
    @Test
    void currentsSpecificRulesCanBeFiltered() {
        // parent's generic is always returned even when not requested, self specific is returned on request
        assertThat(rulesetRepository.findRulesets(currentOrg.businessId(), Set.of("GENERIC_A", "SPECIFIC_C")),
                equalTo(Set.of(parentRuleA, currentRuleC)));
    }

    @Test
    void parentsGenericRuleIsAlwaysReturned() {
        // parent's generic is always returned even when not requested
        assertThat(rulesetRepository.findRulesets(currentOrg.businessId(), Set.of("SPECIFIC_C")),
                equalTo(Set.of(parentRuleA, currentRuleC)));
    }

    @Test
    void parentsSpecificRulesCannotBeSelected() {
        // parent's generic is always returned even when not requested, can't request parent's specific rules
        assertThat(rulesetRepository.findRulesets(currentOrg.businessId(), Set.of("SPECIFIC_B")),
                equalTo(Set.of(parentRuleA)));
    }

    @NotNull
    private ImmutableCooperation partnership(ImmutableOrganization partnerA, ImmutableOrganization partnerB) {
        return ImmutableCooperation.builder()
                .cooperationType(CooperationType.AUTHORITY_PROVIDER)
                .partnerA(partnerA.id())
                .partnerB(partnerB.id())
                .build();
    }
}