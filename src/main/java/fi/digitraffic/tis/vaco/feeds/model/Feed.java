package fi.digitraffic.tis.vaco.feeds.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableFeed.class)
@JsonDeserialize(as = ImmutableFeed.class)
public interface Feed {

    String FEEDREQUEST_PUBLIC_ID = "!!! FEEDREQUEST !!!";
    @Nullable
    String publicId();
    @Value.Parameter
    Company owner();
    @Value.Parameter
    FeedUri uri();
    @Value.Parameter
    TransitDataFormat format();
    @Value.Parameter
    boolean processingEnabled();

}
