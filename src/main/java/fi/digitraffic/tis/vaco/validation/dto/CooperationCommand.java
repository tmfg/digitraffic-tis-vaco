package fi.digitraffic.tis.vaco.validation.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableCooperationCommand.class)
@JsonDeserialize(as = ImmutableCooperationCommand.class)
public interface CooperationCommand {
    CooperationType cooperationType();

    String partnerABusinessId();

    String partnerBBusinessId();
}
