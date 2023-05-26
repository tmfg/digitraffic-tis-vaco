package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableQueueEntry.class)
@JsonDeserialize(as = ImmutableQueueEntry.class)
public interface QueueEntry {
    @Nullable
    @JsonIgnore
    Long id();

    @Nullable
    String publicId();

    String format();

    String url();

    @Nullable
    String etag();

    @Nullable
    JsonNode metadata();

    @Nullable
    List<Phase> phases();

    // XXX: Mapstruct doesn't support Optional type natively at the moment so prefer `@Nullable`, see
    //      https://techlab.bol.com/en/blog/mapstruct-optional-fields/
    //      https://github.com/mapstruct/mapstruct/issues/674

    @Nullable
    ValidationInput validation();

    @Nullable
    ConversionInput conversion();
}
