package fi.digitraffic.tis.vaco.db.model;

import fi.digitraffic.tis.vaco.feeds.model.FeedUri;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.immutables.value.Value;

@Value.Immutable
public interface FeedRecord {
    @Value.Parameter
    Long id();
    @Value.Parameter
    String publicId();
    @Value.Parameter
    Long ownerId();
    @Value.Parameter
    FeedUri uri();
    @Value.Parameter
    TransitDataFormat format();
    @Value.Parameter
    boolean processingEnabled();
}
