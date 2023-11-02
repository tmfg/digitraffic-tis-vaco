package fi.digitraffic.tis.vaco.process;

import com.google.common.collect.ImmutableList;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import fi.digitraffic.tis.exceptions.PersistenceException;
import fi.digitraffic.tis.utilities.MoreGraphs;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.VisibleForTesting;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.delegator.model.TaskCategory;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Service
public class TaskService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TaskRepository taskRepository;
    private final PackagesService packagesService;
    private RulesetService rulesetService;

    public TaskService(TaskRepository taskRepository, PackagesService packagesService, RulesetService rulesetService) {
        this.taskRepository = taskRepository;
        this.packagesService = packagesService;
        this.rulesetService = rulesetService;
    }

    public Task trackTask(Task task, ProcessingState state) {
        logger.trace("Updating task {} to {}", task, state);
        return switch (state) {
            case START -> taskRepository.startTask(task);
            case UPDATE -> taskRepository.updateTask(task);
            case COMPLETE -> taskRepository.completeTask(task);
        };
    }

    public Task findTask(Long entryId, String taskName) {
        return taskRepository.findTask(entryId, taskName);
    }

    public List<Task> findTasks(Entry entry) {
        return Streams.map(taskRepository.findTasks(entry.id()),
                task -> (Task) ImmutableTask.copyOf(task).withPackages(packagesService.findPackages(task)))
                .toList();
    }

    public List<Task> findTasksToExecute(Entry entry) {
        return taskRepository.findAvailableTasksToExecute(entry);
    }

    /**
     * Resolves which tasks should be executed for given entry based on requested validations and configurations.
     * Will also filter list of generated tasks for rules based on given format to prevent running incompatible tasks.
     *
     * @param entry Persisted entry root to which the tasks should be created for
     * @return List of created tasks
     */
    public List<Task> createTasks(Entry entry) {
        List<Task> allTasks = resolveTasks(entry);

        if (!taskRepository.createTasks(allTasks)) {
            throw new PersistenceException("Failed to create all tasks for entry " + entry);
        }

        return findTasks(entry);
    }

    @VisibleForTesting
    protected List<Task> resolveTasks(Entry entry) {

        /*
            1) resolve all tasks which should be part of this entry
            2) toposort + priorization
         */

        List<Task> allTasks = new ArrayList<>();

        List<String> validationTasks = ValidationService.ALL_SUBTASKS;
        allTasks.addAll(createTasks(validationTasks, entry));

        ///

        try {
            logger.info("Generating rule tasks for entry {} based on input format '{}'", entry.publicId(), entry.format());
            TransitDataFormat entryFormat = TransitDataFormat.forField(entry.format());

            List<ImmutableTask> validationRuleTasks = resolveRuleTasks(
                entry,
                Type.VALIDATION_SYNTAX,
                entryFormat,
                Optional.ofNullable(entry.validations())
                    .orElse(List.of())
                    .stream().map(ValidationInput::name)
                    .toList());
            List<ImmutableTask> conversionRuleTasks = resolveRuleTasks(
                entry,
                Type.CONVERSION_SYNTAX,
                entryFormat,
                Optional.ofNullable(entry.conversions())
                    .orElse(List.of())
                    .stream()
                    .map(ConversionInput::name)
                    .toList());

            allTasks.addAll(validationRuleTasks);
            allTasks.addAll(conversionRuleTasks);

            return resolvePrioritiesBasedOnTopology(allTasks);

        } catch (InvalidMappingException ime) {
            logger.warn("Entry {} is requesting operations for unknown input format '{}', skipping rule task generation", entry.publicId(), entry.format());
        }
        return allTasks;
    }

    // TODO: this is currently hardcoded to move on, should be dynamic
    private static final Map<String, List<String>> ruleDeps = ruleDeps();

    private static Map<String, List<String>> ruleDeps() {
        Map<String, List<String>> deps = new HashMap<>();
        deps.put(ValidationService.EXECUTION_SUBTASK, List.of(ValidationService.RULESET_SELECTION_SUBTASK));
        deps.put(ValidationService.RULESET_SELECTION_SUBTASK, List.of(ValidationService.DOWNLOAD_SUBTASK));
        deps.put(RuleName.GTFS_CANONICAL_4_0_0, List.of(ValidationService.DOWNLOAD_SUBTASK));
        deps.put(RuleName.GTFS_CANONICAL_4_1_0, List.of(ValidationService.DOWNLOAD_SUBTASK));
        deps.put(RuleName.NETEX_ENTUR_1_0_1, List.of(ValidationService.DOWNLOAD_SUBTASK));
        return deps;
    }

    /**
     * Analyzes the dependency graph of given tasks and sorts them in topological order, grouping them by
     * non-conflicting dependencies in such a way that each priority group can be run in parallel.
     * <p>
     * Priority group is represented by a multiple of 100, each task in same group is given a sequential index mainly
     * for convenience.
     *
     * @param tasks
     * @return
     */
    @SuppressWarnings("UnstableApiUsage")
    private List<Task> resolvePrioritiesBasedOnTopology(List<Task> tasks) {
        // 1) construct graph
        Map<String, Task> tasksByName = Streams.collect(tasks, Task::name, Function.identity());
        MutableGraph<Task> g = GraphBuilder.directed().build();
        tasks.forEach(g::addNode);
        tasks.forEach(task -> {
            if (ruleDeps.containsKey(task.name())) {
                ruleDeps.get(task.name()).forEach(dep -> g.putEdge(tasksByName.get(dep), task));
            }
        });
        // 2) do the topological ordering
        ImmutableList<Task> order = MoreGraphs.topologicalOrdering(g);

        // 3) resolve priority groups
        Set<String> previousGroupNodes = new HashSet<>();
        List<Task> finalTasks = new ArrayList<>();
        int prioGroup = 1;
        int groupIndex = 0;
        for (Task task : order) {
            if (ruleDeps.getOrDefault(task.name(), List.of()).stream().anyMatch(previousGroupNodes::contains)) {
                prioGroup++;
                groupIndex = 0;
                previousGroupNodes.clear();
            }
            finalTasks.add(ImmutableTask.copyOf(task).withPriority(prioGroup * 100 + groupIndex));

            previousGroupNodes.add(task.name());
            groupIndex++;
        }
        return finalTasks;
    }

    private List<ImmutableTask> resolveRuleTasks(Entry entry,
                                                 Type ruleType,
                                                 TransitDataFormat entryFormat,
                                                 List<String> requestedRuleTasks) {
        Set<Ruleset> allAccessibleRulesets = rulesetService.selectRulesets(entry.businessId(), ruleType, entryFormat, Set.of());
        Map<String, Ruleset> rulesetsByName = Streams
            .filter(allAccessibleRulesets, r -> r.format().equals(entryFormat))
            .collect(Ruleset::identifyingName, Function.identity());

        List<String> allowedRuleTasks = Streams.filter(requestedRuleTasks, rulesetsByName::containsKey).toList();
        logger.debug("Task generation for entry {}: available {} rules {} / requested {} / allowed {}",
            entry.publicId(),
            ruleType,
            rulesetsByName.keySet(),
            requestedRuleTasks,
            allowedRuleTasks);
        return createTasks(allowedRuleTasks, entry);
    }

    private static List<ImmutableTask> resolvePriority(List<String> validationTasks,
                                                       Entry entry,
                                                       TaskCategory category) {
        return Streams.mapIndexed(validationTasks, (i, t) -> ImmutableTask.of(entry.id(), t, category.getPriority() * 100 + i))
            .toList();
    }



    private static List<ImmutableTask> createTasks(List<String> taskNames,
                                                   Entry entry) {
        return Streams.map(taskNames, t -> ImmutableTask.of(entry.id(), t, -1)).toList();
    }


}
