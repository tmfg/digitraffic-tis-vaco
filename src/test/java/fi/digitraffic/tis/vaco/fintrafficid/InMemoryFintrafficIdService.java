package fi.digitraffic.tis.vaco.fintrafficid;

import fi.digitraffic.tis.OverridesConfiguration;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.fintrafficid.model.FintrafficIdGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory variant of {@link FintrafficIdService}. Automatically injected for testing in {@link OverridesConfiguration#fintrafficIdService()}
 */
public class InMemoryFintrafficIdService implements FintrafficIdService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, List<String>> userToGroups;
    private final Map<String, FintrafficIdGroup> groupIdToGroup;

    public InMemoryFintrafficIdService() {
        this.userToGroups = new HashMap<>();
        this.groupIdToGroup = new HashMap<>();
    }

    @Override
    public List<FintrafficIdGroup> getGroups(String oid) {
        List<FintrafficIdGroup> groups = Streams.map(userToGroups.getOrDefault(oid, List.of()), this::getGroup)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
        logger.info("getGroups({}) :: {}", oid, groups);
        return groups;
    }

    @Override
    public Optional<FintrafficIdGroup> getGroup(String groupId) {
        Optional<FintrafficIdGroup> group = Optional.ofNullable(groupIdToGroup.get(groupId));
        logger.info("getGroup({}) :: {}", groupId, group);
        return group;
    }

    public void putGroups(String oid, List<FintrafficIdGroup> groups) {
        groups.forEach(g -> groupIdToGroup.put(g.id(), g));
        userToGroups.put(oid, Streams.collect(groups, FintrafficIdGroup::id));
    }
}
