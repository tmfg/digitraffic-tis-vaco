package fi.digitraffic.tis.vaco.process.model;

import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface JobResult {
    List<PhaseResult<?>> results();
}
