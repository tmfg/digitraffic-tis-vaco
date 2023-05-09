package fi.digitraffic.tis.vaco.queuehandler.mapper;

import fi.digitraffic.tis.vaco.queuehandler.dto.ValidationCommand;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.validation.ValidationView;
import org.mapstruct.Mapper;

import java.math.BigInteger;

@Mapper(componentModel = "spring")
public interface ValidationInputMapper {
    ValidationInput fromValidationCommandToInput(BigInteger entryId, ValidationCommand validationCommand);
    ValidationView fromValidationInput(ValidationInput validationInput);
}
