package fi.digitraffic.tis.vaco.ruleset;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.ImmutablePartnership;
import fi.digitraffic.tis.vaco.company.repository.CompanyHierarchyRepository;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class RulesetServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    CompanyHierarchyRepository companyHierarchyRepository;

    @Autowired
    RulesetService rulesetService;

    private Company fintraffic;
    private Company parentOrg;
    private Company currentOrg;
    private Company otherOrg;
    private Ruleset parentRuleA;
    private Ruleset parentRuleB;
    private Ruleset currentRuleC;
    private Ruleset currentRuleD;
    private Ruleset otherRuleE;
    /**
     * See transit_data_format DB ENUM for valid values. This test uses 'gtfs' because it's quick to write, not because
     * it's a true default.
     */
    private TransitDataFormat testFormat = TransitDataFormat.GTFS;

    @BeforeEach
    void setUp() {
        fintraffic = companyHierarchyRepository.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID).get();
        parentOrg = companyHierarchyRepository.create(TestObjects.aCompany().build());
        currentOrg = companyHierarchyRepository.create(TestObjects.aCompany().build());
        otherOrg = companyHierarchyRepository.create(TestObjects.aCompany().build());
        companyHierarchyRepository.create(partnership(parentOrg, currentOrg));
        companyHierarchyRepository.create(partnership(parentOrg, otherOrg));

        parentRuleA = rulesetService.createRuleset(
            ImmutableRuleset.of(parentOrg.id(), "GENERIC_A", "GENERIC_A", Category.GENERIC, Type.VALIDATION_SYNTAX, testFormat));
        parentRuleB = rulesetService.createRuleset(
                ImmutableRuleset.of(parentOrg.id(), "SPECIFIC_B", "SPECIFIC_B", Category.SPECIFIC, Type.VALIDATION_SYNTAX, testFormat));
        currentRuleC = rulesetService.createRuleset(
                ImmutableRuleset.of(currentOrg.id(), "SPECIFIC_C", "SPECIFIC_C", Category.SPECIFIC, Type.VALIDATION_SYNTAX, testFormat));
        currentRuleD = rulesetService.createRuleset(
                ImmutableRuleset.of(currentOrg.id(), "SPECIFIC_D", "SPECIFIC_D", Category.SPECIFIC, Type.VALIDATION_SYNTAX, testFormat));
        otherRuleE = rulesetService.createRuleset(
                ImmutableRuleset.of(otherOrg.id(), "SPECIFIC_E", "SPECIFIC_E", Category.SPECIFIC, Type.VALIDATION_SYNTAX, testFormat));
    }

    @AfterEach
    void tearDown() {
        companyHierarchyRepository.delete(parentOrg.businessId());
        companyHierarchyRepository.delete(currentOrg.businessId());
        companyHierarchyRepository.delete(otherOrg.businessId());
        rulesetService.deleteRuleset(parentRuleA);
        rulesetService.deleteRuleset(parentRuleB);
        rulesetService.deleteRuleset(currentRuleC);
        rulesetService.deleteRuleset(currentRuleD);
        rulesetService.deleteRuleset(otherRuleE);
    }

    /**
     * Everything under Fintraffic will always get default rules. See `R__seed_data.sql` in DB Migrator repository.
     */
    @Test
    void hasDefaultRulesAlwaysAvailable() {
        Ruleset canonicalGtfsValidator = rulesetService.findByName(RuleName.GTFS_CANONICAL).get();
        Ruleset enturNetexValidator = rulesetService.findByName(RuleName.NETEX_ENTUR).get();
        Ruleset enturNetex2GtfsConverter = rulesetService.findByName(RuleName.NETEX2GTFS_ENTUR).get();
        Ruleset fintrafficGtfs2NetexConverter = rulesetService.findByName(RuleName.GTFS2NETEX_FINTRAFFIC).get();

        assertThat(
            rulesetService.selectRulesets(fintraffic.businessId()),
            equalTo(Set.of(
                canonicalGtfsValidator,
                enturNetexValidator,
                enturNetex2GtfsConverter,
                fintrafficGtfs2NetexConverter)));
    }

    @Test
    void rulesetsAreChosenBasedOnOwnership() {
        assertThat(rulesetService.selectRulesets(parentOrg.businessId(), Type.VALIDATION_SYNTAX, testFormat, Set.of()), equalTo(Set.of(parentRuleA, parentRuleB)));
        assertThat(rulesetService.selectRulesets(otherOrg.businessId(), Type.VALIDATION_SYNTAX, testFormat, Set.of()), equalTo(Set.of(parentRuleA, otherRuleE)));
        assertThat(rulesetService.selectRulesets(currentOrg.businessId(), Type.VALIDATION_SYNTAX, testFormat, Set.of()), equalTo(Set.of(parentRuleA, currentRuleC, currentRuleD)));
    }

    /**
     * @see <a href="https://finrail.atlassian.net/browse/TIS-79">TIS-79</a>
     */
    @Test
    void currentsSpecificRulesCanBeFiltered() {
        // parent's generic is always returned even when not requested, self specific is returned on request
        assertThat(rulesetService.selectRulesets(currentOrg.businessId(), Type.VALIDATION_SYNTAX, testFormat, Set.of("GENERIC_A", "SPECIFIC_C")),
                equalTo(Set.of(parentRuleA, currentRuleC)));
    }

    @Test
    void parentsGenericRuleIsAlwaysReturned() {
        // parent's generic is always returned even when not requested
        assertThat(rulesetService.selectRulesets(currentOrg.businessId(), Type.VALIDATION_SYNTAX, testFormat, Set.of("SPECIFIC_C")),
                equalTo(Set.of(parentRuleA, currentRuleC)));
    }

    @Test
    void parentsSpecificRulesCannotBeSelected() {
        // parent's generic is always returned even when not requested, can't request parent's specific rules
        assertThat(rulesetService.selectRulesets(currentOrg.businessId(), Type.VALIDATION_SYNTAX, testFormat, Set.of("SPECIFIC_B")),
                equalTo(Set.of(parentRuleA)));
    }

    @NotNull
    private ImmutablePartnership partnership(Company partnerA, Company partnerB) {
        return TestObjects.aPartnership()
                .partnerA(partnerA)
                .partnerB(partnerB)
                .build();
    }
}
