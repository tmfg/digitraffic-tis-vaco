package fi.digitraffic.tis.vaco.ruleset;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.RulesetRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.PartnershipRepository;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import fi.digitraffic.tis.vaco.messaging.model.MessageQueue;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesetAccessServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    PartnershipRepository partnershipRepository;

    @Autowired
    RulesetService rulesetService;

    @Autowired
    RulesetAccessService rulesetAccessService;

    @Autowired
    RulesetRepository rulesetRepository;

    @Autowired
    RecordMapper recordMapper;

    private CompanyRecord parentOrg;
    private CompanyRecord currentOrg;
    private CompanyRecord otherOrg;
    private RulesetRecord parentRuleA;
    private RulesetRecord parentRuleB;
    private TransitDataFormat testFormat = TransitDataFormat.GTFS;

    @BeforeAll
    static void beforeAll_initializeSqsQueues() {
        createQueue(MessageQueue.ERRORS.getQueueName());
        createQueue(MessageQueue.RULE_RESULTS_INGEST.getQueueName());
        createQueue(MessageQueue.DLQ.getQueueName());
    }

    @BeforeEach
    void setUp() {
        parentOrg = companyRepository.create(TestObjects.aCompany().build()).get();
        currentOrg = companyRepository.create(TestObjects.aCompany().build()).get();
        otherOrg = companyRepository.create(TestObjects.aCompany().build()).get();
        partnershipRepository.create(PartnershipType.AUTHORITY_PROVIDER, parentOrg, currentOrg);
        partnershipRepository.create(PartnershipType.AUTHORITY_PROVIDER, parentOrg, otherOrg);

        Ruleset ruleset4 = ImmutableRuleset.of("GENERIC_A", "GENERIC_A", Category.GENERIC, RulesetType.VALIDATION_SYNTAX, testFormat);
        parentRuleA = rulesetRepository.createRuleset(parentOrg, ruleset4);
        Ruleset ruleset3 = ImmutableRuleset.of("SPECIFIC_B", "SPECIFIC_B", Category.SPECIFIC, RulesetType.VALIDATION_SYNTAX, testFormat);
        parentRuleB = rulesetRepository.createRuleset(parentOrg, ruleset3);
    }

    @AfterEach
    void tearDown() {
        companyRepository.deleteByBusinessId(parentOrg.businessId());
        companyRepository.deleteByBusinessId(currentOrg.businessId());
        companyRepository.deleteByBusinessId(otherOrg.businessId());
        rulesetRepository.deleteRuleset(parentRuleA);
        rulesetRepository.deleteRuleset(parentRuleB);
    }

    /**
     * Given a company with no grant and no partnership hierarchy path to a ruleset,
     * when a direct grant is created for that company+ruleset,
     * then findCompanyRulesets includes the granted ruleset.
     */
    @Test
    void grantedRulesetIsIncludedInFindCompanyRulesets() {
        // given — otherOrg has no grant for parentRuleB (it's SPECIFIC, created by parentOrg)
        Set<Ruleset> before = rulesetService.findCompanyRulesets(otherOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of());
        assertThat(before, not(hasItem(recordMapper.toRuleset(parentRuleB))));

        // when — grant otherOrg direct access to parentRuleB
        rulesetAccessService.grantAccess(otherOrg.businessId(), parentRuleB.identifyingName());

        // then — parentRuleB now appears in otherOrg's rulesets
        Set<Ruleset> after = rulesetService.findCompanyRulesets(otherOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of());
        assertThat(after, hasItem(recordMapper.toRuleset(parentRuleB)));
    }

    /**
     * Given company A is granted access to a ruleset,
     * when we check company B's rulesets,
     * then company B's rulesets are unchanged.
     */
    @Test
    void grantDoesNotAffectOtherCompanies() {
        // given
        Set<Ruleset> currentOrgBefore = rulesetService.findCompanyRulesets(currentOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of());

        // when — grant otherOrg access to parentRuleB
        rulesetAccessService.grantAccess(otherOrg.businessId(), parentRuleB.identifyingName());

        // then — currentOrg's rulesets unchanged
        Set<Ruleset> currentOrgAfter = rulesetService.findCompanyRulesets(currentOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of());
        assertThat(currentOrgAfter, equalTo(currentOrgBefore));
    }

    /**
     * Given parentOrg is granted direct access to a SPECIFIC ruleset created by another company,
     * when we check parentOrg's child (currentOrg) rulesets,
     * then the grant does NOT cascade to children (grants are non-transitive;
     * only category=generic grants propagate via partnership hierarchy).
     */
    @Test
    void grantIsNotTransitiveToChildren() {
        // given — create a standalone company with a specific ruleset
        CompanyRecord standaloneOrg = companyRepository.create(TestObjects.aCompany().build()).get();
        Ruleset standaloneRuleset = ImmutableRuleset.of("STANDALONE_SPECIFIC", "STANDALONE_SPECIFIC", Category.SPECIFIC, RulesetType.VALIDATION_SYNTAX, testFormat);
        RulesetRecord standaloneRule = rulesetRepository.createRuleset(standaloneOrg, standaloneRuleset);

        try {
            // when — grant parentOrg (which is currentOrg's parent) access to the standalone specific ruleset
            rulesetAccessService.grantAccess(parentOrg.businessId(), standaloneRule.identifyingName());

            // then — parentOrg can see it
            Set<Ruleset> parentRulesets = rulesetService.findCompanyRulesets(parentOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of());
            assertThat(parentRulesets, hasItem(recordMapper.toRuleset(standaloneRule)));

            // but currentOrg (child of parentOrg) cannot — grants are non-transitive
            Set<Ruleset> childRulesets = rulesetService.findCompanyRulesets(currentOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of());
            assertThat(childRulesets, not(hasItem(recordMapper.toRuleset(standaloneRule))));
        } finally {
            rulesetRepository.deleteRuleset(standaloneRule);
            companyRepository.deleteByBusinessId(standaloneOrg.businessId());
        }
    }

    /**
     * Given a company has been granted access to a ruleset,
     * when that grant is revoked,
     * then findCompanyRulesets no longer includes the ruleset.
     */
    @Test
    void revokingGrantRemovesAccess() {
        // given
        rulesetAccessService.grantAccess(otherOrg.businessId(), parentRuleB.identifyingName());
        Set<Ruleset> withGrant = rulesetService.findCompanyRulesets(otherOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of());
        assertThat(withGrant, hasItem(recordMapper.toRuleset(parentRuleB)));

        // when
        rulesetAccessService.revokeAccess(otherOrg.businessId(), parentRuleB.identifyingName());

        // then
        Set<Ruleset> afterRevoke = rulesetService.findCompanyRulesets(otherOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of());
        assertThat(afterRevoke, not(hasItem(recordMapper.toRuleset(parentRuleB))));
    }

    /**
     * Given a grant already exists for a company+ruleset,
     * when the same grant is attempted again,
     * then the operation is idempotent (returns false, no error, grant still active).
     * Mirrors the createPartnership "already exists → Optional.empty()" convention.
     */
    @Test
    void duplicateGrantIsIdempotentOrRejected() {
        // given
        boolean firstGrant = rulesetAccessService.grantAccess(otherOrg.businessId(), parentRuleB.identifyingName());
        assertTrue(firstGrant, "First grant should succeed");

        // when — attempt duplicate grant
        boolean secondGrant = rulesetAccessService.grantAccess(otherOrg.businessId(), parentRuleB.identifyingName());

        // then — idempotent: returns false (already exists), no exception, grant still active
        assertFalse(secondGrant, "Duplicate grant should return false (idempotent no-op)");

        // verify the grant is still in effect
        Set<Ruleset> rulesets = rulesetService.findCompanyRulesets(otherOrg.businessId(), RulesetType.VALIDATION_SYNTAX, testFormat, Set.of());
        assertThat(rulesets, hasItem(recordMapper.toRuleset(parentRuleB)));
    }
}
