package fi.digitraffic.tis.vaco.validation.repository;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.validation.model.Category;
import fi.digitraffic.tis.vaco.validation.model.CooperationType;
import fi.digitraffic.tis.vaco.validation.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.validation.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationRule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    private ImmutableOrganization fintraffic;
    private ImmutableOrganization currentOrg;
    private ImmutableOrganization otherOrg;
    private ImmutableValidationRule parentRuleA;
    private ImmutableValidationRule parentRuleB;
    private ImmutableValidationRule currentRuleC;
    private ImmutableValidationRule currentRuleD;
    private ImmutableValidationRule otherRuleE;

    @BeforeEach
    void setUp() {
        fintraffic = organizationsRepository.findByBusinessId(TestConstants.FINTRAFFIC_BUSINESS_ID);
        currentOrg = organizationsRepository.createOrganization(
                ImmutableOrganization.builder()
                        .name("Esko testaa")
                        .businessId("1234567-8")
                        .build());
        otherOrg = organizationsRepository.createOrganization(
                ImmutableOrganization.builder()
                        .name("tesmaava Esko")
                        .businessId("8765432-1")
                        .build());
        organizationsRepository.createCooperation(partnership(fintraffic, currentOrg));
        organizationsRepository.createCooperation(partnership(fintraffic, otherOrg));

        parentRuleA = rulesetsRepository.createRuleSet(
                ImmutableValidationRule.of(fintraffic.id(), "GENERIC_A", "GENERIC_A", Category.GENERIC));
        parentRuleB = rulesetsRepository.createRuleSet(
                ImmutableValidationRule.of(fintraffic.id(), "SPECIFIC_B", "SPECIFIC_B", Category.SPECIFIC));
        currentRuleC = rulesetsRepository.createRuleSet(
                ImmutableValidationRule.of(currentOrg.id(), "SPECIFIC_C", "SPECIFIC_C", Category.SPECIFIC));
        currentRuleD = rulesetsRepository.createRuleSet(
                ImmutableValidationRule.of(currentOrg.id(), "SPECIFIC_D", "SPECIFIC_D", Category.SPECIFIC));
        otherRuleE = rulesetsRepository.createRuleSet(
                ImmutableValidationRule.of(otherOrg.id(), "SPECIFIC_E", "SPECIFIC_E", Category.SPECIFIC));
    }

    @AfterEach
    void tearDown() {
        organizationsRepository.deleteOrganization(currentOrg.businessId());
        organizationsRepository.deleteOrganization(otherOrg.businessId());
        rulesetsRepository.deleteRuleSet(parentRuleA);
        rulesetsRepository.deleteRuleSet(parentRuleB);
        rulesetsRepository.deleteRuleSet(currentRuleC);
        rulesetsRepository.deleteRuleSet(currentRuleD);
        rulesetsRepository.deleteRuleSet(otherRuleE);
    }

    @Test
    void rulesetsAreChosenBasedOnOwnership() {
        assertThat(rulesetsRepository.findRulesets(fintraffic.businessId()), equalTo(Set.of(parentRuleA, parentRuleB)));
        assertThat(rulesetsRepository.findRulesets(otherOrg.businessId()), equalTo(Set.of(parentRuleA, otherRuleE)));
        assertThat(rulesetsRepository.findRulesets(currentOrg.businessId()), equalTo(Set.of(parentRuleA, currentRuleC, currentRuleD)));
    }

    /**
     * @see <a href="https://finrail.atlassian.net/browse/TIS-79">TIS-79</a>
     */
    @Test
    void currentsSpecificRulesCanBeFiltered() {
        // parent's generic is always returned even when not requested, self specific is returned on request
        assertThat(rulesetsRepository.findRulesets(currentOrg.businessId(), Set.of("GENERIC_A", "SPECIFIC_C")),
                equalTo(Set.of(parentRuleA, currentRuleC)));
    }

    @Test
    void parentsGenericRuleIsAlwaysReturned() {
        // parent's generic is always returned even when not requested
        assertThat(rulesetsRepository.findRulesets(currentOrg.businessId(), Set.of("SPECIFIC_C")),
                equalTo(Set.of(parentRuleA, currentRuleC)));
    }

    @Test
    void parentsSpecificRulesCannotBeSelected() {
        // parent's generic is always returned even when not requested, can't request parent's specific rules
        assertThat(rulesetsRepository.findRulesets(currentOrg.businessId(), Set.of("SPECIFIC_B")),
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
