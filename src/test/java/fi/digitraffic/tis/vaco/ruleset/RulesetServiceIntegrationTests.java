package fi.digitraffic.tis.vaco.ruleset;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.RulesetRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.PartnershipRepository;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
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

    @Autowired
    RulesetRepository rulesetRepository;

    @Autowired
    RecordMapper recordMapper;

    private CompanyRecord fintraffic;
    private CompanyRecord parentOrg;
    private CompanyRecord currentOrg;
    private CompanyRecord otherOrg;
    private RulesetRecord parentRuleA;
    private RulesetRecord parentRuleB;
    private RulesetRecord currentRuleC;
    private RulesetRecord currentRuleD;
    private RulesetRecord otherRuleE;
    /**
     * See transit_data_format DB ENUM for valid values. This test uses 'gtfs' because it's quick to type, not because
     * it's a true default.
     */
    private TransitDataFormat testFormat = TransitDataFormat.GTFS;

    @BeforeAll
    static void beforeAll_initializeSqsQueues() {
        createQueue(MessageQueue.ERRORS.getQueueName());
        createQueue(MessageQueue.RULE_RESULTS_INGEST.getQueueName());
        createQueue(MessageQueue.DLQ.getQueueName());
    }

    @BeforeEach
    void setUp() {
        fintraffic = companyRepository.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID).get();
        parentOrg = companyRepository.create(TestObjects.aCompany().build()).get();
        currentOrg = companyRepository.create(TestObjects.aCompany().build()).get();
        otherOrg = companyRepository.create(TestObjects.aCompany().build()).get();
        partnershipRepository.create(PartnershipType.AUTHORITY_PROVIDER, parentOrg, currentOrg);
        partnershipRepository.create(PartnershipType.AUTHORITY_PROVIDER, parentOrg, otherOrg);

        Ruleset ruleset4 = ImmutableRuleset.of("GENERIC_A", "GENERIC_A", Category.GENERIC, RulesetType.VALIDATION_SYNTAX, testFormat);
        parentRuleA = rulesetRepository.createRuleset(parentOrg, ruleset4);
        Ruleset ruleset3 = ImmutableRuleset.of("SPECIFIC_B", "SPECIFIC_B", Category.SPECIFIC, RulesetType.VALIDATION_SYNTAX, testFormat);
        parentRuleB = rulesetRepository.createRuleset(parentOrg, ruleset3);
        Ruleset ruleset2 = ImmutableRuleset.of("SPECIFIC_C", "SPECIFIC_C", Category.SPECIFIC, RulesetType.VALIDATION_SYNTAX, testFormat);
        currentRuleC = rulesetRepository.createRuleset(currentOrg, ruleset2);
        Ruleset ruleset1 = ImmutableRuleset.of("SPECIFIC_D", "SPECIFIC_D", Category.SPECIFIC, RulesetType.VALIDATION_SYNTAX, testFormat);
        currentRuleD = rulesetRepository.createRuleset(currentOrg, ruleset1);
        Ruleset ruleset = ImmutableRuleset.of("SPECIFIC_E", "SPECIFIC_E", Category.SPECIFIC, RulesetType.VALIDATION_SYNTAX, testFormat);
        otherRuleE = rulesetRepository.createRuleset(otherOrg, ruleset);
    }

    @AfterEach
    void tearDown() {
        companyRepository.deleteByBusinessId(parentOrg.businessId());
        companyRepository.deleteByBusinessId(currentOrg.businessId());
        companyRepository.deleteByBusinessId(otherOrg.businessId());
        rulesetRepository.deleteRuleset(parentRuleA);
        rulesetRepository.deleteRuleset(parentRuleB);
        rulesetRepository.deleteRuleset(currentRuleC);
        rulesetRepository.deleteRuleset(currentRuleD);
        rulesetRepository.deleteRuleset(otherRuleE);
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
        assertRulesets(rulesetService.selectRulesets(parentOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of()), parentRuleA, parentRuleB);
        assertRulesets(rulesetService.selectRulesets(otherOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of()), parentRuleA, otherRuleE);
        assertRulesets(rulesetService.selectRulesets(currentOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of()), parentRuleA, currentRuleC, currentRuleD);
    }

    /**
     * @see <a href="https://finrail.atlassian.net/browse/TIS-79">TIS-79</a>
     */
    @Test
    void currentsSpecificRulesCanBeFiltered() {
        // parent's generic is always returned even when not requested, self specific is returned on request
        assertRulesets(rulesetService.selectRulesets(currentOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of("GENERIC_A", "SPECIFIC_C")), parentRuleA, currentRuleC);
    }

    @Test
    void parentsGenericRuleIsAlwaysReturned() {
        // parent's generic is always returned even when not requested
        assertRulesets(rulesetService.selectRulesets(currentOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of("SPECIFIC_C")), parentRuleA, currentRuleC);
    }

    @Test
    void parentsSpecificRulesCannotBeSelected() {
        // parent's generic is always returned even when not requested, can't request parent's specific rules
        assertRulesets(rulesetService.selectRulesets(currentOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of("SPECIFIC_B")), parentRuleA);
    }

    private void assertRulesets(Set<Ruleset> selectedRulesets, RulesetRecord... expectedRulesets) {
        assertThat(selectedRulesets, equalTo(Streams.map(expectedRulesets, recordMapper::toRuleset).toSet()));
    }
}
