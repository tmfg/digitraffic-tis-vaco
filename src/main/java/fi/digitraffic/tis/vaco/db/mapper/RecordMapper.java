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
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.db.model.ConversionInputRecord;
import fi.digitraffic.tis.vaco.db.model.PartnershipRecord;
import fi.digitraffic.tis.vaco.db.model.ValidationInputRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.model.PersistentEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import fi.digitraffic.tis.vaco.ui.model.Context;
import fi.digitraffic.tis.vaco.ui.model.ImmutableContext;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Map various repository <code>Record</code> types into datamodel equivalents.
 */
@Component
public class RecordMapper {

    private final ObjectMapper objectMapper;

    public RecordMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ImmutableEntry.Builder toEntryBuilder(PersistentEntry persistentEntry, Optional<ContextRecord> context) {
        return ImmutableEntry.builder()
            .publicId(persistentEntry.publicId())
            .name(persistentEntry.name())
            .format(persistentEntry.format())
            .url(persistentEntry.url())
            .businessId(persistentEntry.businessId())
            .etag(persistentEntry.etag())
            .metadata(persistentEntry.metadata())
            .notifications(persistentEntry.notifications())
            .created(persistentEntry.created())
            .started(persistentEntry.started())
            .updated(persistentEntry.updated())
            .completed(persistentEntry.completed())
            .status(persistentEntry.status())
            .context(context.map(ContextRecord::context).orElse(null));
    }

    @SuppressWarnings("unchecked")
    public ValidationInput toValidationInput(ValidationInputRecord validationInputRecord) {
        Class<?> cc = findSubtypeFromAnnotation(validationInputRecord.name());

        return ImmutableValidationInput.builder()
            .id(validationInputRecord.id())
            .name(validationInputRecord.name())
            .config(readValue(objectMapper, validationInputRecord.config(), (Class<RuleConfiguration>) cc))
            .build();
    }

    @SuppressWarnings("unchecked")
    public ConversionInput toConversionInput(ConversionInputRecord conversionInputRecord) {
        Class<?> cc = findSubtypeFromAnnotation(conversionInputRecord.name());

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

    /**
     * Tries to find matching configuration class reference from Jackson's annotations defined in the class based on
     * name of the rule.
     * <p>
     * This method exists to avoid duplicating the type mapping code.
     *
     * @param name Name of the rule
     * @return Matching configuration class reference or null if one couldn't be found.
     */
    private static Class<?> findSubtypeFromAnnotation(String name) {
        JsonSubTypes definedSubTypes = RuleConfiguration.class.getDeclaredAnnotation(JsonSubTypes.class);

        return Streams.filter(definedSubTypes.value(), t -> t.name().equals(name))
            .map(JsonSubTypes.Type::value)
            .findFirst()
            .orElse(null);
    }

    public Company toCompany(CompanyRecord companyRecord) {
        return ImmutableCompany.builder()
            .id(companyRecord.id())
            .businessId(companyRecord.businessId())
            .name(companyRecord.name())
            .contactEmails(companyRecord.contactEmails())
            .language(companyRecord.language())
            .adGroupId(companyRecord.adGroupId())
            .publish(companyRecord.publish())
            .build();
    }

    public Partnership toPartnership(PartnershipRecord partnership,
                                     Function<Long, Company> partnerALoader,
                                     Function<Long, Company> partnerBLoader) {
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
}
