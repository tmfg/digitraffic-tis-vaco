package fi.digitraffic.tis.vaco.company.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fi.digitraffic.tis.vaco.DomainValue;
import org.immutables.value.Value;

import java.util.Set;

/**
 * @deprecated This used to be the root type for previous iteration of company hierarchies, but due to its implicit
 * assumption that there can be only one hierarchy in entire data set it does not fit our current needs anymore. The name
 * <code>hierarchy</code> is important enough though that we do not want to reinvent the name for it, thus this class
 * was renamed to "legacy" hierarchy to keep the backwards compatability without name conflicts.
 */
@Deprecated(since = "2024-10-29")
@DomainValue
@Value.Immutable
@JsonSerialize(as = ImmutableLegacyHierarchy.class)
@JsonDeserialize(as = ImmutableLegacyHierarchy.class)
public interface LegacyHierarchy {

    Company company();

    default Set<LegacyHierarchy> children() {
        return Set.of();
    }
}
