package fi.digitraffic.tis.vaco.feeds.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
@JsonSerialize(as = ImmutableFeedUri.class)
@JsonDeserialize(as = ImmutableFeedUri.class)
public interface FeedUri {

    @Value.Parameter
    String uri();
    @Value.Parameter
    Map<String, String> queryParams();
    @Value.Parameter
    String httpMethod();
    @Value.Parameter
    String requestBody();

}
