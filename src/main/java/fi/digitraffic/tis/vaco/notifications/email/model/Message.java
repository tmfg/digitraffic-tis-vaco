package fi.digitraffic.tis.vaco.notifications.email.model;

import org.immutables.value.Value;

@Value.Immutable
public interface Message {
    @Value.Parameter
    String subject();

    @Value.Parameter
    String body();
}
