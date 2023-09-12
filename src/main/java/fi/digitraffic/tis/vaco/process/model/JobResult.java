package fi.digitraffic.tis.vaco.process.model;

import org.immutables.value.Value;

import java.util.List;

@Deprecated
@Value.Immutable
public interface JobResult {
    @Value.Parameter
    List<TaskResult<?>> results();
}
