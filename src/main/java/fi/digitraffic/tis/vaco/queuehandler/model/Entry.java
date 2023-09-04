package fi.digitraffic.tis.vaco.queuehandler.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.errorhandling.Error;
import fi.digitraffic.tis.vaco.process.model.Phase;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableEntry.class)
@JsonDeserialize(as = ImmutableEntry.class)
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public interface Entry {
    @Nullable
    @JsonView(DataVisibility.Internal.class)
    Long id();

    @Nullable
    String publicId();

    @Value.Parameter
    String format();

    @Value.Parameter
    String url();

    @Value.Parameter
    String businessId();

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
    List<ValidationInput> validations();

    @Nullable
    List<ConversionInput> conversions();

    @Nullable
    List<Error> errors();

    @Nullable
    LocalDateTime created();

    @Nullable
    LocalDateTime started();

    @Nullable
    LocalDateTime updated();

    @Nullable
    LocalDateTime completed();
}
