package fi.digitraffic.tis.vaco.fintrafficid;

import fi.digitraffic.tis.vaco.fintrafficid.model.FintrafficIdGroup;

import java.util.List;
import java.util.Optional;

public interface FintrafficIdService {
    List<FintrafficIdGroup> getGroups(String oid);

    Optional<FintrafficIdGroup> getGroup(String groupId);
}
