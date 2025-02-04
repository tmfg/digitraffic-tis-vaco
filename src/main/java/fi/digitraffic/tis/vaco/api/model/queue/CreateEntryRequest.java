package fi.digitraffic.tis.vaco.api.model.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableCreateEntryRequest.class)
@JsonDeserialize(as = ImmutableCreateEntryRequest.class)
public interface CreateEntryRequest {
    @Schema(description = "Data format. Currently supported: GTFS, NeTEx, GBFS.", example = "gtfs")
    String format();

    @Schema(description = "A meaningful name to identify submitted data. By default: data's file name.", example = "Waltti test")
    String name();

    @Schema(description = "URL containing a downloadable zip with the input data ", example = "https://tvv.fra1.digitaloceanspaces.com/249.zip")
    String url();

    @Nullable
    @Schema(description = "ETag", example = "1234")
    String etag();

    @Schema(description = "Company business ID, in Finnish: Y-tunnus", example = "2942108-7")
    String businessId();

    @Nullable
    @Schema(description = "Freeform metadata field meant for carrying information through the processing pipeline as is.", example = "{}")
    JsonNode metadata();

    @Nullable
    @Schema(description = "Array validation rule names that the input data will be validated against. Possible names can be fetched with rules-controller.", example = "[\"gtfs.canonical\"]")
    List<JsonNode> validations();

    @Nullable
    @Schema(description = "Array conversion rule names that the input data will be converted into." +
        "Possible names can be fetched with rules-controller.", example = "[\"gtfs2netex.fintraffic\"]")
    List<JsonNode> conversions();

    @Value.Default
    @Schema(description = "Array of email addresses to receive a notification once the entry processing is completed.", example = "[\"smth@gmail.com\"]")
    default List<String> notifications() {
        return List.of();
    }

    @Nullable
    @Schema(description = "Some context to group entries with.", example = "Some special testing")
    String context();

    @Nullable
    @Schema(description = "Select one of the predefined credentials to apply for the data source of this entry.")
    String credentials();

    @Value.Default
    default boolean sendNotifications() { return true; }

}
