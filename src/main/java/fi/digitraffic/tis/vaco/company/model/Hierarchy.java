package fi.digitraffic.tis.vaco.company.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Value.Immutable
@JsonSerialize(as = ImmutableHierarchy.class)
@JsonDeserialize(as = ImmutableHierarchy.class)
public interface Hierarchy {

    Company company();

    @Value.Default
    default Set<Hierarchy> children() {
        return Set.of();
    }

    default boolean isMember(String businessId) {
        if (businessId.equals(company().businessId())) {
            return true;
        }
        Set<Hierarchy> ch = children();
        if (ch != null) {
            for (Hierarchy h : ch) {
                if (h.isMember(businessId)) {
                    return true;
                }
            }
        }
        return false;
    }

    default void collectChildren(Map<String, Company> children) {
        children.putIfAbsent(company().businessId(), company());
        Set<Hierarchy> ch = children();
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
    default Optional<Hierarchy> findNode(String businessId) {
        if (company().businessId().equals(businessId)) {
            return Optional.of(this);
        }
        for (Hierarchy c : children()) {
            Optional<Hierarchy> found = c.findNode(businessId);
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
        for (Hierarchy c : children()) {
            if (c.company().businessId().equals(businessId)) {
                return true;
            }
            if (c.hasChildWithBusinessId(businessId)) {
                return true;
            }
        }
        return false;
    }
}
