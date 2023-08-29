package fi.digitraffic.tis.vaco;

import fi.digitraffic.tis.vaco.organization.dto.ImmutableCooperationRequest;
import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableCooperation;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class TestObjects {

    public static ImmutableEntry.Builder anEntry(String format) {
        return ImmutableEntry.builder()
            .format(format)
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
            .validations(List.of());
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

    public static ImmutableCooperationRequest.Builder aCooperationRequest() {
        return ImmutableCooperationRequest.builder()
            .cooperationType(CooperationType.AUTHORITY_PROVIDER);
    }

    public static ImmutableTask.Builder aPhase() {
        return ImmutableTask.builder()
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
