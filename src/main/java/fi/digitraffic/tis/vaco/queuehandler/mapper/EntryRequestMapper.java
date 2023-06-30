package fi.digitraffic.tis.vaco.queuehandler.mapper;

import fi.digitraffic.tis.vaco.queuehandler.dto.EntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EntryRequestMapper {

    ImmutableEntry toEntry(ImmutableEntryRequest entryRequest);

    ValidationInput map(EntryRequest.Validation value);

    ConversionInput map(EntryRequest.Conversion value);
}
