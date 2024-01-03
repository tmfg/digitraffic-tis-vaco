package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Default implementation of {@link ResultProcessor} to be used when rule specific implementation doesn't exist yet.
 */
@Component
public class SimpleResultProcessor extends RuleResultProcessor implements ResultProcessor {
    private final TaskService taskService;

    public SimpleResultProcessor(PackagesService packagesService,
                                 TaskService taskService) {
        super(packagesService);
        this.taskService = Objects.requireNonNull(taskService);
    }

    @Override
    public boolean processResults(ResultMessage resultMessage, Entry entry, Task task) {
        createOutputPackages(resultMessage, entry, task);
        taskService.markStatus(task, Status.SUCCESS);
        return true;
    }
}
