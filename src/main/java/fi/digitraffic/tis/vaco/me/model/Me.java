package fi.digitraffic.tis.vaco.me.model;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.company.model.Company;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
@JsonSerialize(as = ImmutableMe.class)
@JsonDeserialize(as = ImmutableMe.class)
public interface Me {
    @Value.Parameter
    Set<Company> companies();
}
