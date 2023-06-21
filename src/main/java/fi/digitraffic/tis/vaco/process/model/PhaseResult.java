package fi.digitraffic.tis.vaco.process.model;

import org.immutables.value.Value;

/**
 * Implementation note: This might eventually move to more generic package, such as
 * <code>fi.digitraffic.tis.process.model.Result</code> as we want to have a uniform result abstraction for all the
 * phases and steps done by both validation and conversion. At the moment of writing hitting the right level of
 * abstraction is nearly impossible though, which is why this is now in validation subsystem's root package.
 */
@Value.Immutable
public interface PhaseResult<R> {
    @Value.Parameter
    String phase();

    @Value.Parameter
    R result();
}
