package fi.digitraffic.tis.vaco.rules.validation;

import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.process.model.TaskData;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class ValidatorRule implements Rule<ValidationInput, ValidationReport> {

    private final String ruleFormat;
    protected final RulesetRepository rulesetRepository;
    protected final ErrorHandlerService errorHandlerService;

    protected ValidatorRule(
        String ruleFormat,
        RulesetRepository rulesetRepository,
        ErrorHandlerService errorHandlerService) {
        this.ruleFormat = Objects.requireNonNull(ruleFormat);
        this.rulesetRepository = Objects.requireNonNull(rulesetRepository);
        this.errorHandlerService = Objects.requireNonNull(errorHandlerService);
    }

    @Override
    public CompletableFuture<ValidationReport> execute(
        Entry queueEntry,
        Optional<ValidationInput> configuration,
        TaskData<FileReferences> taskData) {

        return CompletableFuture.supplyAsync(() -> {
            if (ruleFormat.equalsIgnoreCase(queueEntry.format())) {
                return runValidator(queueEntry, configuration, taskData);
            } else {
                ImmutableError error = ImmutableError.of(
                    queueEntry.id(),
                    taskData.task().id(),
                    rulesetRepository.findByName(getIdentifyingName()).orElseThrow().id(),
                    "Wrong format! Expected '%s', was '%s'".formatted(ruleFormat, queueEntry.format()));
                errorHandlerService.reportError(error);
                // TODO: 'what' obviously needs something better
                return ImmutableValidationReport.of("what").withErrors(error);
            }
        });
    }

    protected abstract ValidationReport runValidator(
        Entry queueEntry,
        Optional<ValidationInput> configuration,
        TaskData<FileReferences> taskData);
}
