package fi.digitraffic.tis.vaco;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import fi.digitraffic.tis.vaco.configuration.Aws;
import fi.digitraffic.tis.vaco.configuration.AzureAd;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
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
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class TestObjects {

    public static ImmutableEntry.Builder anEntry(String format) {
        return ImmutableEntry.builder()
            .format(format)
            .url("https://testfile")
            .publicId(NanoIdUtils.randomNanoId())
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
            .businessId(randomBusinessId())
            .name("organization:name:" + UUID.randomUUID());
    }

    private static String randomBusinessId() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append(r.nextInt(10));
        }
        return sb.append("-").append(r.nextInt(10)).toString();
    }

    public static ImmutableCooperation.Builder aCooperation() {
        return ImmutableCooperation.builder()
            .cooperationType(CooperationType.AUTHORITY_PROVIDER);
    }

    public static ImmutableCooperationRequest.Builder aCooperationRequest() {
        return ImmutableCooperationRequest.builder()
            .cooperationType(CooperationType.AUTHORITY_PROVIDER);
    }

    public static ImmutableTask.Builder aTask() {
        return ImmutableTask.builder()
            .id(new Random().nextLong())
            .name("task:name:" + UUID.randomUUID())
            .priority(new Random().nextInt());
    }

    public static ImmutableRuleset.Builder aRuleset() {
        return ImmutableRuleset.builder()
            .identifyingName("rule:identifyingName:" + UUID.randomUUID())
            .description("running hello rule from tests")
            .category(Category.GENERIC)
            .type(Type.VALIDATION_SYNTAX);
    }

    public static VacoProperties vacoProperties() {
        String randomSeed = NanoIdUtils.randomNanoId().replaceAll("[-_]", "").toLowerCase();
        Aws aws = new Aws("eu-north-1", Optional.empty(), null, null);
        AzureAd azureAd = new AzureAd("tenantId", "clientId");
        return new VacoProperties("unittests-" + randomSeed, null, "unittests-" + randomSeed + "-processing-bucket", "localhost:5173", "biz", aws, azureAd);
    }
}
