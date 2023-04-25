package fi.digitraffic.tis.vaco.ticket;

import javax.validation.constraints.NotNull;
import java.util.List;

public record TicketStatusView(@NotNull TicketPhaseEnum phase,
                               @NotNull String phaseSummary,
                               List<TicketStep> steps) {
}

enum TicketPhaseEnum {
    IN_PROGRESS, SUCCESS, ERROR
}

