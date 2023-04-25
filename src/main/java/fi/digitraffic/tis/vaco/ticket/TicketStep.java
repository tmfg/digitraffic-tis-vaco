package fi.digitraffic.tis.vaco.ticket;

import javax.validation.constraints.NotNull;

public record TicketStep(@NotNull String phase,
                         @NotNull long timestamp) {
}
