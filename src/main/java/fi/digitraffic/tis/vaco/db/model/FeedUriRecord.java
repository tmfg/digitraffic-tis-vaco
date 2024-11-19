package fi.digitraffic.tis.vaco.db.model;

import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
public interface FeedUriRecord {
    String uri();
    Map<String, String> queryParams();
    String httpMethod();
    String requestBody();
}
