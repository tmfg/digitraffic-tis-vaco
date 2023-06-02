package fi.digitraffic.tis.vaco.validation.model;

import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface ValidationJobResult {
    List<Result<?>> results();
}
