package fi.digitraffic.tis.vaco.feeds.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableFeed.class)
@JsonDeserialize(as = ImmutableFeed.class)
public interface Feed {
    @Nullable
    String publicId();
    @Value.Parameter
    String owner();
    @Value.Parameter
    FeedUri uri();
    @Value.Parameter
    String format();
    @Value.Parameter
    boolean processingEnabled();

}
