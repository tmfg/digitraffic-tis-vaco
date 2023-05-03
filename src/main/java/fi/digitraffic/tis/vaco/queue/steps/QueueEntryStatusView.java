package fi.digitraffic.tis.vaco.queue.steps;

import javax.validation.constraints.NotNull;
import java.util.List;

public record QueueEntryStatusView(@NotNull QueueEntryStatusEnum phase,
                                   @NotNull String phaseSummary,
                                   List<QueueStepView> steps) {
}

enum QueueEntryStatusEnum {
    IN_PROGRESS, SUCCESS, ERROR
}

