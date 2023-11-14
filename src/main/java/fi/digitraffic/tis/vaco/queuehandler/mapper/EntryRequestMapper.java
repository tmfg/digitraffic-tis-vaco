package fi.digitraffic.tis.vaco.queuehandler.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.Strings;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.queuehandler.dto.EntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class EntryRequestMapper {

    private final ObjectMapper objectMapper;

    public EntryRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ImmutableEntry toEntry(EntryRequest entryRequest) {
        if (entryRequest == null) {
            return null;
        }

        return ImmutableEntry.builder()
            .name(entryRequest.getName())
            .format(safeTrim(entryRequest.getFormat()))
            .url(safeTrim(entryRequest.getUrl()))
            .businessId(safeTrim(entryRequest.getBusinessId()))
            .etag(strip("\"", safeTrim(entryRequest.getEtag()), "\""))
            .metadata(entryRequest.getMetadata())
            .validations(mapValidations(entryRequest.getValidations()))
            .conversions(mapConversions(entryRequest.getConversions()))
            .build();
    }

    /**
     * Trim given string, avoiding nulls and BOMs.
     * @param s String to trim
     * @return Trimmed string or null if original string was null to begin with
     * @see Strings#stripBOM(String)
     */
    private static String safeTrim(String s) {
        if (s == null) {
            return null;
        }
        return Strings.stripBOM(s).trim();
    }

    /**
     * Strip given start and end from content string if present.
     *
     * @param prefix Prefix to remove
     * @param content Unaltered content
     * @param suffix Suffix to remove
     * @return stripped content or null if content was null to begin with
     */
    private static String strip(String prefix, String content, String suffix) {
        if (content == null) {
            return null;
        }
        if (content.startsWith(prefix)) {
            content = content.substring(prefix.length());
        }
        if (content.endsWith(suffix)) {
            content = content.substring(0, content.length() - suffix.length());
        }
        return content;
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

        return Streams.map(conversions, conversion -> fromJson(conversion, ConversionInput.class)).toList();
    }

    protected <T> T fromJson(JsonNode data, Class<T> type) {
        try {
            return objectMapper.treeToValue(data, type);
        } catch (JsonProcessingException e) {
            throw new InvalidMappingException("Could not map JsonNode to " + type, e);
        }
    }
}
