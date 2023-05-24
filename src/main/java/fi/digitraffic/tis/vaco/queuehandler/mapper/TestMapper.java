package fi.digitraffic.tis.vaco.queuehandler.mapper;

import fi.digitraffic.tis.vaco.queuehandler.dto.YayDto;
import fi.digitraffic.tis.vaco.queuehandler.model.Yay;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TestMapper {

    Yay fromDto(YayDto testDto);
}
