package fi.digitraffic.tis.vaco;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.api.model.feed.ImmutableCreateFeedRequest;
import fi.digitraffic.tis.vaco.api.model.queue.ImmutableCreateEntryRequest;
import fi.digitraffic.tis.vaco.company.dto.ImmutablePartnershipRequest;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.ImmutablePartnership;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.configuration.Aws;
import fi.digitraffic.tis.vaco.configuration.AzureAd;
import fi.digitraffic.tis.vaco.configuration.Cleanup;
import fi.digitraffic.tis.vaco.configuration.Email;
import fi.digitraffic.tis.vaco.configuration.MagicLink;
import fi.digitraffic.tis.vaco.configuration.MsGraph;
import fi.digitraffic.tis.vaco.configuration.S3;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.feeds.model.ImmutableFeedUri;
import fi.digitraffic.tis.vaco.findings.model.ImmutableFinding;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ui.model.ImmutableContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class TestObjects {

    public static ImmutableCreateFeedRequest.Builder aCreateFeedRequest() {
        return ImmutableCreateFeedRequest.builder()
            .format(TransitDataFormat.SIRI_ET)
            .owner("public-validation-test-id")
            .processingEnabled(true)
            .uri(aFeedUri());
    }

    public static ImmutableFeedUri aFeedUri() {
        return ImmutableFeedUri.builder()
            .uri("www.example.fi/v1/feeds")
            .httpMethod("POST")
            .putQueryParams("key", "value")
            .requestBody("x")
            .build();
    }

    public static ImmutableEntry.Builder anEntry(String format) {
        return ImmutableEntry.builder()
            .name("testName")
            .format(format)
            .url("https://testfile")
            .publicId(NanoIdUtils.randomNanoId())
            .businessId(Constants.FINTRAFFIC_BUSINESS_ID);
    }

    public static ImmutableCreateEntryRequest.Builder aValidationEntryRequest() {
        return ImmutableCreateEntryRequest.builder()
            .format("gtfs")
            .name("fileName")
            .url("https://example.fi")
            .etag("etag")
            .businessId(Constants.FINTRAFFIC_BUSINESS_ID)
            .validations(List.of());
    }

    public static ImmutableEntry.Builder anEntry() {
        return ImmutableEntry.builder()
            .publicId(NanoIdUtils.randomNanoId())
            .format("gtfs")
            .name("fileName")
            .url("https://example.fi")
            .etag("etag")
            .name("yay")
            .businessId(Constants.FINTRAFFIC_BUSINESS_ID);
    }

    public static ImmutableCompany.Builder aCompany() {
        return ImmutableCompany.builder()
            .businessId(randomBusinessId())
            .name("company:name:" + UUID.randomUUID())
            .publish(true);
    }

    private static String randomBusinessId() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append(r.nextInt(10));
        }
        return sb.append("-").append(r.nextInt(10)).toString();
    }

    public static ImmutableContext.Builder aContext(String businessId) {
        return ImmutableContext.builder().context(UUID.randomUUID().toString()).businessId(businessId);
    }

    public static ImmutablePartnership.Builder aPartnership() {
        return ImmutablePartnership.builder()
            .type(PartnershipType.AUTHORITY_PROVIDER);
    }

    public static ImmutablePartnershipRequest.Builder aPartnershipRequest() {
        return ImmutablePartnershipRequest.builder();
    }

    /**
     * @deprecated Arbitrary tasks cannot be created directly anymore
     */
    @Deprecated
    public static ImmutableTask.Builder aTask(Entry entry) {
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
            .type(RulesetType.VALIDATION_SYNTAX);
    }

    public static VacoProperties vacoProperties() {
        return vacoProperties(null, null, null, null, null, null);
    }

    /**
     * Override specific configuration subtypes. Provide nulls for those values which should use defaults.
     */
    public static VacoProperties vacoProperties(Aws aws, AzureAd azureAd, Email email, MagicLink magicLink, Cleanup cleanup, MsGraph msGraph) {
        String randomSeed = NanoIdUtils.randomNanoId().replaceAll("[-_]", "").toLowerCase();

        return new VacoProperties(
            "unittests-" + randomSeed,
            null,
            "unittests-" + randomSeed + "-processing-bucket",
            "http://localhost:5173",
            "http://localhost:8080/api",
            "biz",
            aws != null ? aws : new Aws("eu-north-1", null, null, null, new S3(null)),
            azureAd != null ? azureAd : new AzureAd("tenantId", "clientId"),
            email != null ? email : new Email("king@commonwealth", null),
            magicLink != null ? magicLink : new MagicLink("C7AS{&MrNsFUzEXbpBJ4j@DLu2(vP=$3"),
            cleanup != null ? cleanup : new Cleanup(Duration.parse("-P-365D"), 10, 100),
            msGraph != null ? msGraph : new MsGraph("tenantId", "clientId", "clientSecret", "schemaExtension"));
    }

    public static JwtAuthenticationToken jwtAuthenticationToken(String oid) {
        String fakeGroupId = UUID.randomUUID().toString();
        Jwt jwt = new Jwt("ignored", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), Map.of("headers", "cannot be empty"), Map.of("groups", List.of(fakeGroupId), "oid", oid));
        return new JwtAuthenticationToken(jwt);
    }

    public static JwtAuthenticationToken jwtAdminAuthenticationToken(String oid) {
        String fakeGroupId = UUID.randomUUID().toString();
        Jwt jwt = new Jwt("ignored", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), Map.of("headers", "cannot be empty"), Map.of("groups", List.of(fakeGroupId), "oid", oid, "roles", List.of("vaco.admin")));

        return new JwtAuthenticationToken(jwt);
    }

    public static JwtAuthenticationToken jwtCompanyAdminAuthenticationToken(String oid) {
        String fakeGroupId = UUID.randomUUID().toString();
        Jwt jwt = new Jwt("ignored", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), Map.of("headers", "cannot be empty"), Map.of("groups", List.of(fakeGroupId), "oid", oid, "roles", List.of("vaco.company_admin")));

        return new JwtAuthenticationToken(jwt);
    }

    public static JwtAuthenticationToken jwtNoGroupsToken(String oid) {
        Jwt jwt = new Jwt("ignored", Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), Map.of("headers", "cannot be empty"), Map.of("oid", oid));
        return new JwtAuthenticationToken(jwt);
    }

    public static ImmutableFinding.Builder aFinding(long rulesetId, long taskId) {
        return ImmutableFinding.builder()
            .rulesetId(rulesetId)
            .taskId(taskId);
    }
}
