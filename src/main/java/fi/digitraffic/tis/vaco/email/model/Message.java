package fi.digitraffic.tis.vaco.email.model;

import org.immutables.value.Value;

@Value.Immutable
public interface Message {
    String subject();

    String body();
}
