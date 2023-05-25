package fi.digitraffic.tis.vaco.queuehandler.mapper;

import fi.digitraffic.tis.vaco.queuehandler.dto.entry.EntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import org.mapstruct.Mapper;

import java.math.BigInteger;

@Mapper(componentModel = "spring")
public interface ValidationInputMapper {
    ValidationInput fromValidationCommandToInput(BigInteger entryId, EntryCommand.Validation validation);
}
