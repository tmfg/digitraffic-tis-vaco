package fi.digitraffic.tis.vaco.queuehandler.mapper;

import fi.digitraffic.tis.vaco.queuehandler.dto.Metadata;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EntryMapper {
    Entry fromMetadataToEntry(String publicId, Metadata metadata);
    Metadata fromEntryToMetadata(Entry entry);
}
