package fi.digitraffic.tis.vaco.company.service.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Hierarchy;
import org.immutables.value.Value;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Special variant of {@link Hierarchy} which contains only business id references.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableLightweightHierarchy.class)
@JsonDeserialize(as = ImmutableLightweightHierarchy.class)
public interface LightweightHierarchy {

    @Value.Parameter
    String businessId();

    @Value.Parameter
    Set<LightweightHierarchy> children();

    default boolean isMember(String businessId) {
        if (businessId.equals(businessId())) {
            return true;
        }
        Set<LightweightHierarchy> ch = children();
        if (ch != null) {
            for (LightweightHierarchy h : ch) {
                if (h.isMember(businessId)) {
                    return true;
                }
            }
        }
        return false;
    }

    default void collectChildren(Map<String, String> children) {
        children.putIfAbsent(businessId(), businessId());
        Set<LightweightHierarchy> ch = children();
        if (ch != null) {
            ch.forEach(child -> child.collectChildren(children));
        }
    }

    /**
     * If this hierarchy contains as (sub) hierarchy the given company as matched by {@link Company#businessId()}},
     * return the matching hierarchy node. May return self. Returns {@link Optional#empty()} if nothing is found.
     * @param businessId Business id to match
     * @return Hierarchy node matching the given company or empty if one wasn't found.
     */
    default Optional<LightweightHierarchy> findNode(String businessId) {
        if (businessId().equals(businessId)) {
            return Optional.of(this);
        }
        for (LightweightHierarchy c : children()) {
            Optional<LightweightHierarchy> found = c.findNode(businessId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Check if any children have a matching business id.
     * @param businessId Business id to check.
     * @return True if any child has a matching business id, false otherwise.
     */
    default boolean hasChildWithBusinessId(String businessId) {
        for (LightweightHierarchy c : children()) {
            if (c.businessId().equals(businessId)) {
                return true;
            }
            if (c.hasChildWithBusinessId(businessId)) {
                return true;
            }
        }
        return false;
    }

    static LightweightHierarchy from(Hierarchy h) {
        return null;
    }
}
