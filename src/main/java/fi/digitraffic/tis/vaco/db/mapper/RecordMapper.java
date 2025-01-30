package fi.digitraffic.tis.vaco.db.mapper;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.ImmutablePartnership;
import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.credentials.model.AuthenticationDetails;
import fi.digitraffic.tis.vaco.credentials.model.Credentials;
import fi.digitraffic.tis.vaco.credentials.model.ImmutableCredentials;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.db.model.ConversionInputRecord;
import fi.digitraffic.tis.vaco.db.model.CredentialsRecord;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.model.FeatureFlagRecord;
import fi.digitraffic.tis.vaco.db.model.FeedRecord;
import fi.digitraffic.tis.vaco.db.model.FindingRecord;
import fi.digitraffic.tis.vaco.db.model.PackageRecord;
import fi.digitraffic.tis.vaco.db.model.PartnershipRecord;
import fi.digitraffic.tis.vaco.db.model.RulesetRecord;
import fi.digitraffic.tis.vaco.db.model.TaskRecord;
import fi.digitraffic.tis.vaco.db.model.ValidationInputRecord;
import fi.digitraffic.tis.vaco.db.model.notifications.SubscriptionRecord;
import fi.digitraffic.tis.vaco.featureflags.model.FeatureFlag;
import fi.digitraffic.tis.vaco.featureflags.model.ImmutableFeatureFlag;
import fi.digitraffic.tis.vaco.feeds.model.Feed;
import fi.digitraffic.tis.vaco.feeds.model.ImmutableFeed;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.findings.model.ImmutableFinding;
import fi.digitraffic.tis.vaco.notifications.model.ImmutableSubscription;
import fi.digitraffic.tis.vaco.notifications.model.Subscription;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ui.model.Context;
import fi.digitraffic.tis.vaco.ui.model.ImmutableContext;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongFunction;

/**
 * Map various repository <code>Record</code> types into datamodel equivalents.
 */
@Component
public class RecordMapper {

    private final ObjectMapper objectMapper;

    public RecordMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ImmutableEntry.Builder toEntryBuilder(EntryRecord entryRecord, Optional<ContextRecord> context, Optional<CredentialsRecord> credentials) {
        return ImmutableEntry.builder()
            .publicId(entryRecord.publicId())
            .name(entryRecord.name())
            .format(entryRecord.format())
            .url(entryRecord.url())
            .businessId(entryRecord.businessId())
            .etag(entryRecord.etag())
            .metadata(entryRecord.metadata())
            .notifications(entryRecord.notifications())
            .created(entryRecord.created())
            .started(entryRecord.started())
            .updated(entryRecord.updated())
            .completed(entryRecord.completed())
            .status(entryRecord.status())
            .context(context.map(ContextRecord::context).orElse(null))
            .credentials(credentials.map(CredentialsRecord::publicId).orElse(null))
            .sendNotifications(entryRecord.sendNotifications());
    }

    @SuppressWarnings("unchecked")
    public ValidationInput toValidationInput(ValidationInputRecord validationInputRecord) {
        Class<?> cc = findSubtypeFromAnnotation(validationInputRecord.name(), RuleConfiguration.class);

        return ImmutableValidationInput.builder()
            .name(validationInputRecord.name())
            .config(readValue(objectMapper, validationInputRecord.config(), (Class<RuleConfiguration>) cc))
            .build();
    }

    @SuppressWarnings("unchecked")
    public ConversionInput toConversionInput(ConversionInputRecord conversionInputRecord) {
        Class<?> cc = findSubtypeFromAnnotation(conversionInputRecord.name(), RuleConfiguration.class);

        return ImmutableConversionInput.builder()
            .id(conversionInputRecord.id())
            .name(conversionInputRecord.name())
            .config(readValue(objectMapper, conversionInputRecord.config(), (Class<RuleConfiguration>) cc))
            .build();
    }

    private static <O> O readValue(ObjectMapper objectMapper, JsonNode json, Class<O> type) {
        if (type == null) {
            return null;
        }
        try {
            return objectMapper.treeToValue(json, type);
        } catch (JsonProcessingException e) {
            throw new InvalidMappingException("Failed to read JSONB as valid " + type, e);
        }
    }

    private static <O> O readValue(ObjectMapper objectMapper, byte[] bytes, Class<O> type) {
        if (type == null) {
            return null;
        }
        try {
            return objectMapper.readValue(bytes, type);
        } catch (IOException e) {
            throw new InvalidMappingException("Failed to read JSONB as valid " + type, e);
        }
    }

    /**
     * Tries to find matching configuration class reference from Jackson's annotations defined in the class based on
     * name of the rule.
     * <p>
     * This method exists to avoid duplicating the type mapping code.
     *
     * @param name Name of the rule
     * @param aClass
     * @return Matching configuration class reference or null if one couldn't be found.
     */
    private static <T> Class<T> findSubtypeFromAnnotation(String name, Class<T> aClass) {
        JsonSubTypes definedSubTypes = aClass.getDeclaredAnnotation(JsonSubTypes.class);

        return (Class<T>) Streams.filter(definedSubTypes.value(), t -> t.name().equals(name))
            .map(JsonSubTypes.Type::value)
            .findFirst()
            .orElse(null);
    }

    public Company toCompany(CompanyRecord companyRecord) {
        return ImmutableCompany.builder()
            .businessId(companyRecord.businessId())
            .name(companyRecord.name())
            .contactEmails(companyRecord.contactEmails())
            .language(companyRecord.language())
            .adGroupId(companyRecord.adGroupId())
            .publish(companyRecord.publish())
            .codespaces(companyRecord.codespaces())
            .notificationWebhookUri(companyRecord.notificationWebhookUri())
            .website(companyRecord.website())
            .build();
    }

    public Partnership toPartnership(PartnershipRecord partnership,
                                     LongFunction<Company> partnerALoader,
                                     LongFunction<Company> partnerBLoader) {
        return ImmutablePartnership.of(
            partnership.type(),
            partnerALoader.apply(partnership.partnerA()),
            partnerBLoader.apply(partnership.partnerB())
        );
    }

    public Context toContext(ContextRecord contextRecord, String businessId) {
        return ImmutableContext.builder()
            .context(contextRecord.context())
            .businessId(businessId)
            .build();
    }

    public Task toTask(TaskRecord taskRecord) {
        return ImmutableTask.builder()
            .id(taskRecord.id())
            .publicId(taskRecord.publicId())
            .name(taskRecord.name())
            .priority(taskRecord.priority())
            .created(taskRecord.created())
            .started(taskRecord.started())
            .updated(taskRecord.updated())
            .completed(taskRecord.completed())
            .status(taskRecord.status())
            .build();
    }

    public FeatureFlag toFeatureFlag(FeatureFlagRecord featureFlagRecord) {
        return ImmutableFeatureFlag.builder()
            .name(featureFlagRecord.name())
            .enabled(featureFlagRecord.enabled())
            .modified(featureFlagRecord.modified())
            .modifiedBy(featureFlagRecord.modifiedBy())
            .build();
    }

    public Ruleset toRuleset(RulesetRecord rulesetRecord) {
        return ImmutableRuleset.builder()
            .id(rulesetRecord.id())
            .publicId(rulesetRecord.publicId())
            .identifyingName(rulesetRecord.identifyingName())
            .description(rulesetRecord.description())
            .category(rulesetRecord.category())
            .type(rulesetRecord.type())
            .format(rulesetRecord.format())
            .beforeDependencies(rulesetRecord.beforeDependencies())
            .afterDependencies(rulesetRecord.afterDependencies())
            .build();
    }

    public Package toPackage(@Nullable Task task, PackageRecord packageRecord) {
        return ImmutablePackage.of(
            task,
            packageRecord.name(),
            packageRecord.path()
        );
    }

    public Finding toFinding(FindingRecord findingRecord) {
        return ImmutableFinding.builder()
            .id(findingRecord.id())
            .publicId(findingRecord.publicId())
            .taskId(findingRecord.taskId())
            .rulesetId(findingRecord.rulesetId())
            .source(findingRecord.source())
            .message(findingRecord.message())
            .severity(findingRecord.severity())
            .raw(findingRecord.raw())
            .build();
    }

    public Subscription toSubscription(SubscriptionRecord subscriptionRecord,
                                       CompanyRecord subscriber,
                                       CompanyRecord resource) {
        return ImmutableSubscription.builder()
            .publicId(subscriptionRecord.publicId())
            .type(subscriptionRecord.type())
            .subscriber(toCompany(subscriber))
            .resource(toCompany(resource))
            .build();
    }

    public Feed toFeed(FeedRecord feedRecord, CompanyRecord companyRecord) {
        return ImmutableFeed.builder()
            .publicId(feedRecord.publicId())
            .owner(toCompany(companyRecord))
            .format(feedRecord.format())
            .uri(feedRecord.uri())
            .processingEnabled(feedRecord.processingEnabled())
            .build();
    }

    public Credentials toCredentials(CredentialsRecord credentialsRecord, CompanyRecord companyRecord) {
        Class<AuthenticationDetails> cc = findSubtypeFromAnnotation(credentialsRecord.type().fieldName(), AuthenticationDetails.class);

        return ImmutableCredentials.builder()
            .publicId(credentialsRecord.publicId())
            .type(credentialsRecord.type())
            .name(credentialsRecord.name())
            .description(credentialsRecord.description())
            .owner(toCompany(companyRecord))
            .details(readValue(objectMapper, credentialsRecord.details(), cc))
            .build();
    }
}
