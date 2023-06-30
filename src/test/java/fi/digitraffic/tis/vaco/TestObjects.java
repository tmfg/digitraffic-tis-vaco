package fi.digitraffic.tis.vaco;

import fi.digitraffic.tis.vaco.organization.dto.ImmutableCooperationRequest;
import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableValidation;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;

import java.util.Random;
import java.util.UUID;

public class TestObjects {

    public static ImmutableEntry.Builder anEntry() {
        return ImmutableEntry.builder()
            .format("gtfs")
            .url("https://testfile")
            .publicId("testPublicId")
            .businessId(TestConstants.FINTRAFFIC_BUSINESS_ID);
    }

    public static ImmutableEntryRequest.Builder aValidationEntryRequest() {
        return ImmutableEntryRequest.builder()
            .format("gtfs")
            .url("https://example.fi")
            .etag("etag")
            .businessId(TestConstants.FINTRAFFIC_BUSINESS_ID)
            .validation(ImmutableValidation.builder().build());
    }

    public static ImmutableOrganization.Builder anOrganization() {
        return ImmutableOrganization.builder()
            .businessId(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString());
    }

    public static ImmutableCooperation.Builder aCooperation() {
        return ImmutableCooperation.builder()
            .cooperationType(CooperationType.AUTHORITY_PROVIDER);
    }

    public static ImmutableCooperationRequest.Builder aCooperationCommand() {
        return ImmutableCooperationRequest.builder()
            .cooperationType(CooperationType.AUTHORITY_PROVIDER);
    }

    public static ImmutablePhase.Builder aPhase() {
        return ImmutablePhase.builder()
            .id(new Random().nextLong())
            .name(UUID.randomUUID().toString())
            .priority(new Random().nextInt());
    }

    public static ImmutableRuleset.Builder aRuleset() {
        return ImmutableRuleset.builder()
            .identifyingName(UUID.randomUUID().toString())
            .description("running hello rule from tests")
            .category(Category.GENERIC)
            .type(Type.VALIDATION_SYNTAX);
    }
}
