package fi.digitraffic.tis.vaco.api.model.feed;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.feeds.model.FeedUri;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCreateFeedRequest.class)
@JsonDeserialize(as = ImmutableCreateFeedRequest.class)
public interface CreateFeedRequest {

    @Value.Parameter
    String owner();
    @Value.Parameter
    FeedUri uri();
    @Value.Parameter
    TransitDataFormat format();
    @Value.Parameter
    boolean processingEnabled();

}
