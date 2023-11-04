package fi.digitraffic.tis.vaco.rules.internal;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
public class SelectRulesetsRule implements Rule<Entry, Set<Ruleset>> {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TaskService taskService;
    private final RulesetService rulesetService;

    public SelectRulesetsRule(TaskService taskService, RulesetService rulesetService) {
        this.taskService = Objects.requireNonNull(taskService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
    }

    @Override
    public String getIdentifyingName() {
        return ValidationService.RULESET_SELECTION_SUBTASK;
    }

    @Override
    public CompletableFuture<Set<Ruleset>> execute(Entry entry) {
        return CompletableFuture.supplyAsync(() -> {
            Task task = taskService.trackTask(taskService.findTask(entry.id(), ValidationService.RULESET_SELECTION_SUBTASK), ProcessingState.START);

            TransitDataFormat format;
            try {
                format = TransitDataFormat.forField(entry.format());
            } catch (InvalidMappingException ime) {
                logger.warn("Cannot select rulesets for entry {}: Unknown format '{}'", entry.publicId(), entry.format(), ime);
                return Set.of();
            }

            // find all possible rulesets to execute
            Set<Ruleset> rulesets = Streams.filter(
                    rulesetService.selectRulesets(
                        entry.businessId(),
                        Type.VALIDATION_SYNTAX,
                        format,
                        Streams.map(entry.validations(), ValidationInput::name).toSet()),
                    // filter to contain only format compatible rulesets
                    r -> r.identifyingName().startsWith(entry.format() + "."))
                .toSet();

            taskService.trackTask(task, ProcessingState.COMPLETE);

            logger.info("Selected rulesets for {} are {}", entry.publicId(), Streams.collect(rulesets, Ruleset::identifyingName));

            return rulesets;
        });
    }
}
