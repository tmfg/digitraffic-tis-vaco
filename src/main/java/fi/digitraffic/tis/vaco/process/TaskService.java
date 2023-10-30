package fi.digitraffic.tis.vaco.process;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TaskRepository taskRepository;
    private final PackagesService packagesService;

    public TaskService(TaskRepository taskRepository, PackagesService packagesService) {
        this.taskRepository = taskRepository;
        this.packagesService = packagesService;
    }

    public Task trackTask(Task task, ProcessingState state) {
        // TODO: add checks which prevent already started task from re-starting
        logger.info("Updating task {} to {}", task, state);
        return switch (state) {
            case START -> taskRepository.startTask(task);
            case UPDATE -> taskRepository.updateTask(task);
            case COMPLETE -> taskRepository.completeTask(task);
        };
    }

    public Task findTask(Long entryId, String taskName) {
        return taskRepository.findTask(entryId, taskName);
    }

    public boolean createTasks(List<Task> tasks) {
        return taskRepository.createTasks(tasks);
    }

    public List<Task> findTasks(Entry entry) {
        return Streams.map(taskRepository.findTasks(entry.id()),
                task -> (Task) ImmutableTask.copyOf(task).withPackages(packagesService.findPackages(task)))
                .toList();
    }
}
