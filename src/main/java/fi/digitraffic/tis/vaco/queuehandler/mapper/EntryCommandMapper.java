package fi.digitraffic.tis.vaco.queuehandler.mapper;

import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryCommand;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableQueueEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EntryCommandMapper {

    ImmutableQueueEntry toQueueEntry(ImmutableEntryCommand entryCommand);

    ValidationInput map(ImmutableEntryCommand.Validation value);

    ConversionInput map(ImmutableEntryCommand.Conversion value);
}
