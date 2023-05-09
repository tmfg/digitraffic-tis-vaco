package fi.digitraffic.tis.vaco.queuehandler.mapper;

import fi.digitraffic.tis.vaco.queuehandler.dto.PhaseView;
import fi.digitraffic.tis.vaco.queuehandler.model.Phase;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.sql.Timestamp;

@Mapper(componentModel = "spring")
public interface PhaseMapper {

    @Mapping(target = "timestamp", source = "started")
    PhaseView fromPhaseToPhaseView(Phase phase);

    default long map(Timestamp timestamp) {
        return timestamp == null ? 0 : timestamp.getTime();
    }
}
