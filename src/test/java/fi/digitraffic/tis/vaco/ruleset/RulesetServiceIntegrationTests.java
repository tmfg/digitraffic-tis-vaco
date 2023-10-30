package fi.digitraffic.tis.vaco.ruleset;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.organization.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.organization.model.Organization;
import fi.digitraffic.tis.vaco.organization.repository.CooperationRepository;
import fi.digitraffic.tis.vaco.organization.repository.OrganizationRepository;
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
    CooperationRepository cooperationRepository;

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    RulesetService rulesetService;

    private Organization fintraffic;
    private Organization parentOrg;
    private Organization currentOrg;
    private Organization otherOrg;
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
        fintraffic = organizationRepository.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID).get();
        parentOrg = organizationRepository.create(TestObjects.anOrganization().build());
        currentOrg = organizationRepository.create(TestObjects.anOrganization().build());
        otherOrg = organizationRepository.create(TestObjects.anOrganization().build());
        cooperationRepository.create(partnership(parentOrg, currentOrg));
        cooperationRepository.create(partnership(parentOrg, otherOrg));

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
        organizationRepository.delete(parentOrg.businessId());
        organizationRepository.delete(currentOrg.businessId());
        organizationRepository.delete(otherOrg.businessId());
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
        Ruleset canonicalGtfsValidator400 = rulesetService.findByName(RuleName.GTFS_CANONICAL_4_0_0).get();
        Ruleset canonicalGtfsValidator410 = rulesetService.findByName(RuleName.GTFS_CANONICAL_4_1_0).get();
        Ruleset enturNetexValidator = rulesetService.findByName(RuleName.NETEX_ENTUR_1_0_1).get();

        assertThat(
            rulesetService.selectRulesets(fintraffic.businessId()),
            equalTo(Set.of(canonicalGtfsValidator400, canonicalGtfsValidator410, enturNetexValidator)));
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
    private ImmutableCooperation partnership(Organization partnerA, Organization partnerB) {
        return TestObjects.aCooperation()
                .partnerA(partnerA)
                .partnerB(partnerB)
                .build();
    }
}
