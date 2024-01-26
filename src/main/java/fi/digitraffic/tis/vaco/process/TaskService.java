package fi.digitraffic.tis.vaco.process;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import fi.digitraffic.tis.exceptions.PersistenceException;
import fi.digitraffic.tis.utilities.MoreGraphs;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.internal.StopsAndQuaysRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.validation.RulesetSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

@Service
public class TaskService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TaskRepository taskRepository;
    private final PackagesService packagesService;
    private final RulesetService rulesetService;
    private final CachingService cachingService;

    public TaskService(TaskRepository taskRepository,
                       PackagesService packagesService,
                       RulesetService rulesetService,
                       CachingService cachingService) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.cachingService = Objects.requireNonNull(cachingService);
    }

    public Task trackTask(Entry entry, Task task, ProcessingState state) {
        logger.trace("Updating task {} to {}", task, state);
        Task result = switch (state) {
            case START -> taskRepository.startTask(task);
            case UPDATE -> taskRepository.updateTask(task);
            case COMPLETE -> taskRepository.completeTask(task);
        };

        cachingService.invalidateEntry(entry);

        return result;
    }

    public Optional<Task> findTask(Long entryId, String taskName) {
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

        List<Task> tasks = findTasks(entry);

        cachingService.invalidateEntry(entry);

        return tasks;
    }

    @VisibleForTesting
    protected List<Task> resolveTasks(Entry entry) {

        Set<Task> allTasks = new HashSet<>();

        try {
            logger.debug("Generating rule tasks for entry {} based on input format '{}'", entry.publicId(), entry.format());

            List<ImmutableTask> ruleTasks = resolveRuleTasks(
                entry,
                Stream.concat(
                    Optional.ofNullable(entry.validations()).orElse(List.of()).stream().map(ValidationInput::name),
                    Optional.ofNullable(entry.conversions()).orElse(List.of()).stream().map(ConversionInput::name))
                .toList());

            allTasks.addAll(ruleTasks);

            List<Task> prioritizedTasks = resolvePrioritiesBasedOnTopology(List.copyOf(allTasks));
            logger.info("Generated tasks for entry {}: {}", entry.publicId(), prioritizedTasks);
            return prioritizedTasks;
        } catch (InvalidMappingException ime) {
            logger.warn("Entry {} is requesting operations for unsupported input format '{}', skipping rule task generation", entry.publicId(), entry.format(), ime);
        }
        return List.of();
    }

    // these hardcoded values represent the dependencies which don't have a Ruleset in database and thus there isn't a
    // better place for declaring them
    private static final Map<String, List<String>> ruleDeps = ruleDeps();

    private static Map<String, List<String>> ruleDeps() {
        Map<String, List<String>> deps = new HashMap<>();
        deps.put(DownloadRule.DOWNLOAD_SUBTASK, List.of());
        deps.put(StopsAndQuaysRule.STOPS_AND_QUAYS_TASK, List.of());
        deps.put(RulesetSubmissionService.VALIDATE_TASK,
            List.of(
                DownloadRule.DOWNLOAD_SUBTASK,
                StopsAndQuaysRule.STOPS_AND_QUAYS_TASK));
        deps.put(RulesetSubmissionService.CONVERT_TASK, conversionDeps());
        return deps;
    }

    private static List<String> conversionDeps() {
        List<String> deps = new ArrayList<>();
        // conversion rules may declare validations as dependencies, thus they need to be executed before running the
        // conversion rule itself
        deps.addAll(RuleName.ALL_EXTERNAL_VALIDATION_RULES);
        deps.addAll(List.of(
            DownloadRule.DOWNLOAD_SUBTASK,
            StopsAndQuaysRule.STOPS_AND_QUAYS_TASK,
            RulesetSubmissionService.VALIDATE_TASK));
        return deps;
    }

    /**
     * Analyzes the dependency graph of given tasks and sorts them in topological order, grouping them by
     * non-conflicting dependencies in such a way that each priority group can be run in parallel.
     * <p>
     * Priority group is represented by a multiple of 100, each task in same group is given a sequential index mainly
     * for convenience.
     *
     * @param tasks Tasks to prioritize and group.
     * @return New list with a copy of tasks updated with priorities.
     */
    @SuppressWarnings("UnstableApiUsage")
    private List<Task> resolvePrioritiesBasedOnTopology(List<Task> tasks) {
        // 1) construct graph
        Map<String, Task> tasksByName = Streams.collect(tasks, Task::name, Function.identity());
        MutableGraph<Task> g = GraphBuilder.directed().build();
        tasks.forEach(g::addNode);
        tasks.forEach(task -> {

            if (ruleDeps.containsKey(task.name())) {
                ruleDeps.get(task.name()).forEach(dep -> {
                    if (tasksByName.containsKey(dep)) {
                        g.putEdge(tasksByName.get(dep), task);
                    }
                });
            } else {
                rulesetService.findByName(task.name()).ifPresent(ruleset ->
                    ruleset.dependencies().forEach(dep -> {
                        if (tasksByName.containsKey(dep)) {
                            g.putEdge(tasksByName.get(dep), task);
                        }
                    }));
            }});
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
                                                 List<String> requestedRuleTasks) {
        Set<Ruleset> allAccessibleRulesets = rulesetService.selectRulesets(entry.businessId());
        Map<String, Ruleset> rulesetsByName = Streams
            .collect(allAccessibleRulesets, Ruleset::identifyingName, Function.identity());

        Set<String> allowedRuleTasks = Streams.filter(requestedRuleTasks, rulesetsByName::containsKey).toSet();

        // include ruleset dependencies to list of resolved tasks
        Set<String> dependencies = Streams.filter(allAccessibleRulesets, ruleset -> allowedRuleTasks.contains(ruleset.identifyingName()))
            .flatten(Ruleset::dependencies)
            .toSet();
        // include transitive dependencies as well
        dependencies.addAll(Streams.filter(dependencies, rulesetsByName::containsKey)
            .flatten(d -> rulesetsByName.get(d).dependencies())
                .toSet());

        logger.debug("Task generation for entry {}: available rules {} / requested {} / allowed {} / dependencies {}",
            entry.publicId(),
            rulesetsByName.keySet(),
            requestedRuleTasks,
            allowedRuleTasks,
            dependencies);
        List<String> completeTasks = new ArrayList<>(allowedRuleTasks);
        completeTasks.addAll(dependencies);
        return createTasks(completeTasks, entry);
    }

    private static List<ImmutableTask> createTasks(List<String> taskNames,
                                                   Entry entry) {
        return Streams.map(taskNames, t -> ImmutableTask.of(entry.id(), t, -1)).toList();
    }

    public boolean areAllTasksCompleted(Entry entry) {
        return taskRepository.areAllTasksCompleted(entry);
    }

    public Task markStatus(Entry entry, Task task, Status status) {
        Task marked = taskRepository.markStatus(task, status);
        cachingService.invalidateEntry(entry);
        return marked;
    }
}
