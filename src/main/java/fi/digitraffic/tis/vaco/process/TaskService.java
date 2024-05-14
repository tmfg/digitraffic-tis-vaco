package fi.digitraffic.tis.vaco.process;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import fi.digitraffic.tis.exceptions.PersistenceException;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.rules.internal.StopsAndQuaysRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
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
    private final RulesetService rulesetService;
    private final CachingService cachingService;

    public TaskService(TaskRepository taskRepository,
                       RulesetService rulesetService,
                       CachingService cachingService) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
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

        cachingService.invalidateEntry(entry.publicId());

        return result;
    }

    public Optional<Task> findTask(Long entryId, String taskName) {
        return taskRepository.findTask(entryId, taskName);
    }

    public Optional<Task> findTask(String publicId, String taskName) {
        return taskRepository.findTask(publicId, taskName);
    }

    public List<Task> findTasks(Entry entry) {
        return taskRepository.findTasks(entry.publicId());
    }

    public List<Task> findTasks(EntryRecord entry) {
        return taskRepository.findTasks(entry.id());
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
    public List<Task> createTasks(EntryRecord entry) {
        List<Task> allTasks = resolveTasks(entry);

        if (!taskRepository.createTasks(entry, allTasks)) {
            throw new PersistenceException("Failed to create all tasks for entry " + entry);
        }

        List<Task> tasks = findTasks(entry);

        cachingService.invalidateEntry(entry.publicId());

        return tasks;
    }

    @VisibleForTesting
    protected List<Task> resolveTasks(EntryRecord entry) {

        Set<Task> allTasks = new HashSet<>();

        try {
            logger.debug("Generating rule tasks for entry {} based on input format '{}'", entry.publicId(), entry.format());

            List<ImmutableTask> ruleTasks = resolveRuleTasks(
                entry,
                Stream.concat(
                    Optional.ofNullable(findValidationInputs(entry)).orElse(List.of()).stream().map(ValidationInput::name),
                    Optional.ofNullable(findConversionInputs(entry)).orElse(List.of()).stream().map(ConversionInput::name))
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

    public List<ValidationInput> findValidationInputs(EntryRecord entry) {
        return taskRepository.findValidationInputs(entry);
    }

    public List<ConversionInput> findConversionInputs(EntryRecord entry) {
        return taskRepository.findConversionInputs(entry);
    }

    // these hardcoded values represent the dependencies which don't have a Ruleset in database and thus there isn't a
    // better place for declaring them
    private static final Map<String, List<String>> ruleDeps = ruleDeps();

    private static Map<String, List<String>> ruleDeps() {
        Map<String, List<String>> deps = new HashMap<>();
        deps.put(DownloadRule.PREPARE_DOWNLOAD_TASK, List.of());
        deps.put(StopsAndQuaysRule.PREPARE_STOPS_AND_QUAYS_TASK, List.of());
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
    private List<Task> resolvePrioritiesBasedOnTopology(List<Task> tasks) {
        // 0) prerequisite lookups
        Map<String, Task> tasksByName = Streams.collect(tasks, Task::name, Function.identity());
        // 1) construct graph
        MutableGraph<Task> dag = constructGraph(tasksByName);
        // 2) do the topological sorting
        List<Task> sorted = sortTopologically(tasksByName, dag);
        // 3) resolve priority groups
        return groupForParallelism(sorted);
    }

    private MutableGraph<Task> constructGraph(Map<String, Task> tasks) {
        MutableGraph<Task> dag = GraphBuilder.directed().allowsSelfLoops(false).build();
        tasks.values().forEach(task -> {
            dag.addNode(task);
            // built-in pseudorules
            if (ruleDeps.containsKey(task.name())) {
                ruleDeps.get(task.name()).forEach(dep -> {
                    if (tasks.containsKey(dep)) {
                        dag.putEdge(tasks.get(dep), task);
                    }
                });
            } else {
                rulesetService.findByName(task.name()).ifPresent(ruleset -> {
                    // add "before" edges
                    ruleset.beforeDependencies().forEach(dep -> {
                        if (tasks.containsKey(dep)) {
                            dag.putEdge(tasks.get(dep), task);
                        }
                    });
                    // add "after" edges
                    ruleset.afterDependencies().forEach(dep -> {
                        if (tasks.containsKey(dep)) {
                            dag.putEdge(task, tasks.get(dep));
                        }
                    });
                });
            }
        });
        return dag;
    }

    /**
     * Topological sort using <a href="https://en.wikipedia.org/wiki/Topological_sorting#Kahn's_algorithm" target="_blank">Kahn's algorithm</a>.
     * @param tasks
     * @param dag
     * @return
     */
    private List<Task> sortTopologically(Map<String, Task> tasks, MutableGraph<Task> dag) {
        // L ← Empty list that will contain the sorted elements
        List<Task> sorted = new ArrayList<>();
        // S ← Set of all nodes with no incoming edge
        Map<String, Task> roots = Streams.filter(tasks.values(), t -> dag.predecessors(t).isEmpty()).collect(Task::name, Function.identity());
        List<Task> rootKeys = new ArrayList<>(roots.values());
        // while S is not empty do)
        while (!rootKeys.isEmpty()) {
            Task n = rootKeys.get(0);
            //    remove a node n from S
            rootKeys.remove(n);
            //    add n to L
            sorted.add(n);
            //    for each node m with an edge e from n to m do
            List.copyOf(dag.successors(n)).forEach(m -> {
                //        remove edge e from the graph
                dag.removeEdge(n, m);
                //        if m has no other incoming edges then
                if (dag.predecessors(m).isEmpty()) {
                    //            insert m into S
                    roots.put(m.name(), m);
                    rootKeys.add(m);
                }
            });
        }
        // if graph has edges then
        if (!dag.edges().isEmpty()) {
            throw new InvalidMappingException("graph has referential cycles, cannot sort");
        }
        return sorted;
    }

    private List<Task> groupForParallelism(List<Task> sorted) {
        List<Task> finalTasks = new ArrayList<>();
        int prioGroup = 1;
        int groupIndex = 0;

        Map<String, Ruleset> rulesets = Streams.map(sorted, t -> rulesetService.findByName(t.name()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Ruleset::identifyingName, Function.identity());

        for (int i = 0; i < sorted.size(); i++) {
            Set<String> previous = Streams.map((i == 0) ? List.of() : sorted.subList(0, i), Task::name).toSet();
            Task current = sorted.get(i);

            if (rulesets.containsKey(current.name())) {
                Ruleset ruleset = rulesets.get(current.name());
                if (anyPreviousTaskIsInCurrentTasksBeforeDependencies(previous, ruleset)
                    || anyPreviousTaskHasCurrentAsAfterDependency(rulesets, previous, ruleset)) {
                    prioGroup++;
                    groupIndex = 0;
                }
            }

            finalTasks.add(ImmutableTask.copyOf(current).withPriority(prioGroup * 100 + groupIndex));
            groupIndex++;
        }
        return finalTasks;
    }

    private boolean anyPreviousTaskIsInCurrentTasksBeforeDependencies(Set<String> before, Ruleset current) {
        return current.beforeDependencies().stream().anyMatch(before::contains);
    }

    private boolean anyPreviousTaskHasCurrentAsAfterDependency(Map<String, Ruleset> rulesets, Set<String> before, Ruleset current) {
        for (String possible : before) {
            if (rulesets.containsKey(possible)) {
                return rulesets.get(possible).afterDependencies().stream().anyMatch(s -> s.equals(current.identifyingName()));
            }
        }
        return false;
    }

    private List<ImmutableTask> resolveRuleTasks(EntryRecord entry,
                                                 List<String> requestedRuleTasks) {
        Set<Ruleset> allAccessibleRulesets = rulesetService.selectRulesets(entry.businessId());
        Map<String, Ruleset> rulesetsByName = Streams
            .collect(allAccessibleRulesets, Ruleset::identifyingName, Function.identity());

        Set<String> allowedRuleTasks = Streams.filter(requestedRuleTasks, rulesetsByName::containsKey).toSet();

        // include ruleset dependencies to list of resolved tasks
        Set<Ruleset> rulesets = Streams.filter(allAccessibleRulesets, ruleset -> allowedRuleTasks.contains(ruleset.identifyingName())).toSet();
        Set<String> preDependencies = Streams.flatten(rulesets, Ruleset::beforeDependencies).toSet();
        Set<String> postDependencies = Streams.flatten(rulesets, Ruleset::afterDependencies).toSet();
        // include transitive dependencies as well
        preDependencies.addAll(Streams.filter(preDependencies, rulesetsByName::containsKey)
            .flatten(d -> rulesetsByName.get(d).beforeDependencies())
            .toSet());
        postDependencies.addAll(Streams.filter(postDependencies, rulesetsByName::containsKey)
            .flatten(d -> rulesetsByName.get(d).afterDependencies())
            .toSet());

        logger.debug("Task generation for entry {}: available rules {} / requested {} / allowed {} / pre-dependencies {} / post-dependencies {}",
            entry.publicId(),
            rulesetsByName.keySet(),
            requestedRuleTasks,
            allowedRuleTasks,
            preDependencies,
            postDependencies);
        List<String> completeTasks = new ArrayList<>(allowedRuleTasks);
        completeTasks.addAll(preDependencies);
        completeTasks.addAll(postDependencies);
        return createTasks(completeTasks, entry);
    }

    private static List<ImmutableTask> createTasks(List<String> taskNames,
                                                   EntryRecord entry) {
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

    /**
     * Looks up possible internal task dependencies and inspects their success for given entry+task pair.
     *
     * @param entry Entry which should be inspected
     * @param task Task which dependencies should all be successful
     * @return True if all dependent tasks are successful, fail otherwise.
     */
    public boolean internalDependenciesCompletedSuccessfully(Entry entry, String task) {
        List<String> deps = ruleDeps().getOrDefault(task, List.of());
        Map<String, Task> byName = Streams.collect(entry.tasks(), Task::name, Function.identity());
        for (String dep : deps) {
            if (byName.containsKey(dep)) {
                Status taskStatus = byName.get(dep).status();
                if (Status.CANCELLED.equals(taskStatus)) {
                    return false;
                }
            }
        }
        return true;
    }


    public Optional<Task> findTask(String taskPublicId) {
        return taskRepository.findTask(taskPublicId);
    }
}
