package fi.digitraffic.tis.vaco.company.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

import java.util.Map;
import java.util.Set;

@Value.Immutable
@JsonSerialize(as = ImmutableHierarchy.class)
@JsonDeserialize(as = ImmutableHierarchy.class)
public interface Hierarchy {

    Company company();

    @Nullable
    Set<Hierarchy> children();

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
}
