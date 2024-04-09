package fi.digitraffic.tis.vaco.ruleset;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.PartnershipRepository;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class RulesetServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    PartnershipRepository partnershipRepository;

    @Autowired
    RulesetService rulesetService;

    private CompanyRecord fintraffic;
    private CompanyRecord parentOrg;
    private CompanyRecord currentOrg;
    private CompanyRecord otherOrg;
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
        fintraffic = companyRepository.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID).get();
        parentOrg = companyRepository.create(TestObjects.aCompany().build());
        currentOrg = companyRepository.create(TestObjects.aCompany().build());
        otherOrg = companyRepository.create(TestObjects.aCompany().build());
        partnershipRepository.create(PartnershipType.AUTHORITY_PROVIDER, parentOrg, currentOrg);
        partnershipRepository.create(PartnershipType.AUTHORITY_PROVIDER, parentOrg, otherOrg);

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
        companyRepository.delete(parentOrg.businessId());
        companyRepository.delete(currentOrg.businessId());
        companyRepository.delete(otherOrg.businessId());
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
        Ruleset enturGbfsValidator = rulesetService.findByName(RuleName.GBFS_ENTUR).get();

        // XXX: Old ones are kept until we have cleaned up rest of the data. See TIS-193
        Ruleset oldVersionedCanonicalGtfsValidator400 = rulesetService.findByName(RuleName.GTFS_CANONICAL + ".v4_0_0").get();
        Ruleset oldVersionedCanonicalGtfsValidator410 = rulesetService.findByName(RuleName.GTFS_CANONICAL + ".v4_1_0").get();
        Ruleset oldEnturNetexValidator101 = rulesetService.findByName(RuleName.NETEX_ENTUR + ".v1_0_1").get();
        Ruleset oldEnturNetex2GtfsConverter206 = rulesetService.findByName(RuleName.NETEX2GTFS_ENTUR + ".v2_0_6").get();
        Ruleset oldFintrafficGtfs2NetexConverter100 = rulesetService.findByName(RuleName.GTFS2NETEX_FINTRAFFIC + ".v1_0_0").get();
        assertThat(
            Streams.collect(rulesetService.selectRulesets(fintraffic.businessId()), Ruleset::identifyingName),
            equalTo(Streams.collect(Set.of(
                    canonicalGtfsValidator,
                    enturNetexValidator,
                    enturNetex2GtfsConverter,
                    fintrafficGtfs2NetexConverter,
                    enturGbfsValidator,
                    oldVersionedCanonicalGtfsValidator400,
                    oldVersionedCanonicalGtfsValidator410,
                    oldEnturNetexValidator101,
                    oldEnturNetex2GtfsConverter206,
                    oldFintrafficGtfs2NetexConverter100),
                Ruleset::identifyingName)));
    }

    @Test
    void publicValidationTestHasRules() {
        Set<Ruleset> publicValidationTestRules = rulesetService.selectRulesets(Constants.PUBLIC_VALIDATION_TEST_ID);
        Assertions.assertFalse(publicValidationTestRules.isEmpty());
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
}
