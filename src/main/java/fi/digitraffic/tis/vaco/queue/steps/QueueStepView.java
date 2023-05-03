package fi.digitraffic.tis.vaco.queue.steps;

import javax.validation.constraints.NotNull;

public record QueueStepView(@NotNull QueueStep step,
                            @NotNull String stepSummary,
                            @NotNull long timestamp) {
}
