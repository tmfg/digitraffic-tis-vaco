package fi.digitraffic.tis.vaco.queue.steps;

import javax.validation.constraints.NotNull;
import java.util.List;

public record QueueEntryStatusView(@NotNull QueueEntryStatus phase,
                                   @NotNull String phaseSummary,
                                   List<QueueStepView> steps) {
}

enum QueueEntryStatus {
    IN_PROGRESS, SUCCESS, ERROR
}

