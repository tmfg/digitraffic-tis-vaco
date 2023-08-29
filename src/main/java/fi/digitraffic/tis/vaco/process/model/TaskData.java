package fi.digitraffic.tis.vaco.process.model;

import jakarta.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
public interface TaskData<P> {
    @Nullable
    P payload();

    @Value.Parameter
    Task task();

}
