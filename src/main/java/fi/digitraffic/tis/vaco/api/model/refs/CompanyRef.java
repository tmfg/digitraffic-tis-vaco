package fi.digitraffic.tis.vaco.api.model.refs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = fi.digitraffic.tis.vaco.api.model.refs.ImmutableCompanyRef.class)
@JsonDeserialize(as = fi.digitraffic.tis.vaco.api.model.refs.ImmutableCompanyRef.class)
public interface CompanyRef {
    @Value.Parameter
    String businessId();
}
