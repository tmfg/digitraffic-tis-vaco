package fi.digitraffic.tis.vaco.db.model;

import fi.digitraffic.tis.vaco.feeds.model.FeedUri;
import org.immutables.value.Value;

@Value.Immutable
public interface FeedRecord {
    @Value.Parameter
    Long id();
    @Value.Parameter
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
