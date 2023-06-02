package fi.digitraffic.tis.vaco.validation.model;

import fi.digitraffic.tis.vaco.queuehandler.model.ImmutablePhase;
import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
public interface PhaseData<P> {
    @Nullable
    P payload();

    @Value.Parameter
    ImmutablePhase phase();

}
