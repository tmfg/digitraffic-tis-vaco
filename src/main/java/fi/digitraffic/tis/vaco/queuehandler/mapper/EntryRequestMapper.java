package fi.digitraffic.tis.vaco.queuehandler.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.validation.model.InvalidMappingException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class EntryRequestMapper {

    private final ObjectMapper objectMapper;

    public EntryRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ImmutableEntry toEntry(ImmutableEntryRequest entryRequest) {
        if (entryRequest == null) {
            return null;
        }

        return ImmutableEntry.builder()
            .format(entryRequest.getFormat())
            .url(entryRequest.getUrl())
            .businessId(entryRequest.getBusinessId())
            .etag(entryRequest.getEtag())
            .metadata(entryRequest.getMetadata())
            .validations(mapValidations(entryRequest.getValidations()))
            .conversions(mapConversions(entryRequest.getConversions()))
            .build();
    }

    private Iterable<ValidationInput> mapValidations(List<JsonNode> validations) {
        if (validations == null) {
            return List.of();
        }

        return Streams.map(validations, validation -> fromJson(validation, ValidationInput.class)).toList();
    }

    private Iterable<ConversionInput> mapConversions(List<JsonNode> conversions) {
        if (conversions == null) {
            return List.of();
        }

        return Streams.map(conversions, validation -> fromJson(validation, ConversionInput.class)).toList();
    }

    protected <T> T fromJson(JsonNode data, Class<T> type) {
        try {
            return objectMapper.treeToValue(data, type);
        } catch (JsonProcessingException e) {
            throw new InvalidMappingException("Could not map JsonNode to ValidationInput", e);
        }
    }
}
