package fi.digitraffic.tis.vaco.process;

import fi.digitraffic.tis.utilities.model.ProcessingState;
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

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public ImmutableTask trackTask(Task task, ProcessingState state) {
        logger.info("Updating task {} to {}", task, state);
        return switch (state) {
            case START -> taskRepository.startTask(task);
            case UPDATE -> taskRepository.updateTask(task);
            case COMPLETE -> taskRepository.completeTask(task);
        };
    }

    public ImmutableTask findTask(Long entryId, String phaseName) {
        return taskRepository.findTask(entryId, phaseName);
    }

    public boolean createTasks(List<ImmutableTask> phases) {
        return taskRepository.createTasks(phases);
    }

    public List<ImmutableTask> findTasks(Entry entry) {
        return taskRepository.findTasks(entry.id());
    }
}
