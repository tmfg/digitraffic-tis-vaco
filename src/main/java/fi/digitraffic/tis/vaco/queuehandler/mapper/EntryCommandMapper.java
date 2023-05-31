package fi.digitraffic.tis.vaco.queuehandler.mapper;

import fi.digitraffic.tis.vaco.queuehandler.dto.EntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableQueueEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EntryCommandMapper {

    ImmutableQueueEntry toQueueEntry(EntryCommand entryCommand);

    ValidationInput map(EntryCommand.Validation value);

    ConversionInput map(EntryCommand.Conversion value);
}
