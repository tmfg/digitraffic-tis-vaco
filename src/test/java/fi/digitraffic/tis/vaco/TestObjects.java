package fi.digitraffic.tis.vaco;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.admintasks.model.ImmutableGroupIdMappingTask;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.configuration.Aws;
import fi.digitraffic.tis.vaco.configuration.AzureAd;
import fi.digitraffic.tis.vaco.configuration.Email;
import fi.digitraffic.tis.vaco.configuration.S3;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.company.dto.ImmutablePartnershipRequest;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.ImmutablePartnership;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
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
            .id(new Random().nextLong())
            .name("testName")
            .format(format)
            .url("https://testfile")
            .publicId(NanoIdUtils.randomNanoId())
            .businessId(Constants.FINTRAFFIC_BUSINESS_ID);
    }

    public static ImmutableEntryRequest.Builder aValidationEntryRequest() {
        return ImmutableEntryRequest.builder()
            .format("gtfs")
            .name("fileName")
            .url("https://example.fi")
            .etag("etag")
            .businessId(Constants.FINTRAFFIC_BUSINESS_ID)
            .validations(List.of());
    }

    public static ImmutableEntry.Builder anEntry() {
        return ImmutableEntry.builder()
            .format("gtfs")
            .name("fileName")
            .url("https://example.fi")
            .etag("etag")
            .businessId(Constants.FINTRAFFIC_BUSINESS_ID);
    }

    public static ImmutableCompany.Builder aCompany() {
        return ImmutableCompany.builder()
            .businessId(randomBusinessId())
            .name("company:name:" + UUID.randomUUID());
    }

    private static String randomBusinessId() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append(r.nextInt(10));
        }
        return sb.append("-").append(r.nextInt(10)).toString();
    }

    public static ImmutablePartnership.Builder aPartnership() {
        return ImmutablePartnership.builder()
            .type(PartnershipType.AUTHORITY_PROVIDER);
    }

    public static ImmutablePartnershipRequest.Builder aPartnershipRequest() {
        return ImmutablePartnershipRequest.builder()
            .type(PartnershipType.AUTHORITY_PROVIDER);
    }

    /**
     * @deprecated Arbitrary tasks cannot be created directly anymore
     */
    @Deprecated
    public static ImmutableTask.Builder aTask(Entry entry) {
        return ImmutableTask.builder()
            .id(new Random().nextLong())
            .entryId(entry.id())
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
        return vacoProperties(null, null, null);
    }

    /**
     * Override specific configuration subtypes. Provide nulls for those values which should use defaults.
     */
    public static VacoProperties vacoProperties(Aws aws, AzureAd azureAd, Email email) {
        String randomSeed = NanoIdUtils.randomNanoId().replaceAll("[-_]", "").toLowerCase();

        return new VacoProperties(
            "unittests-" + randomSeed,
            null,
            "unittests-" + randomSeed + "-processing-bucket",
            "http://localhost:5173",
            "biz",
            aws != null ? aws : new Aws("eu-north-1", null, null, null, new S3(null)),
            azureAd != null ? azureAd : new AzureAd("tenantId", "clientId"),
            email != null ? email : new Email("king@commonwealth", null));
    }

    public static ImmutableGroupIdMappingTask.Builder adminGroupId() {
        return adminGroupId(UUID.randomUUID().toString());
    }

    public static ImmutableGroupIdMappingTask.Builder adminGroupId(String groupId) {
        return ImmutableGroupIdMappingTask
            .builder()
            .groupId(groupId);
    }
}
