package fi.digitraffic.tis.vaco.db.model;

import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
public interface RulesetRecord {

    Long id();

    String publicId();

    Long ownerId();

    String identifyingName();

    String description();

    Category category();

    Type type();

    TransitDataFormat format();

    @Value.Default
    default Set<String> beforeDependencies() {
        return Set.of();
    }

    @Value.Default
    default Set<String> afterDependencies() {
        return Set.of();
    }
}
