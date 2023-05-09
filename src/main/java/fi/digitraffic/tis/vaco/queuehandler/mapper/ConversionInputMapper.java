package fi.digitraffic.tis.vaco.queuehandler.mapper;

import fi.digitraffic.tis.vaco.conversion.ConversionView;
import fi.digitraffic.tis.vaco.queuehandler.dto.ConversionCommand;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import org.mapstruct.Mapper;

import java.math.BigInteger;

@Mapper(componentModel = "spring")
public interface ConversionInputMapper {
    ConversionInput fromConversionCommandToInput(BigInteger entryId, ConversionCommand conversionCommand);
    ConversionView fromConversionInputToView(ConversionInput conversionInput);
}
