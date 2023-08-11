package fi.digitraffic.tis.vaco.validation.rules;

import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.process.model.PhaseData;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.validation.model.FileReferences;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;

import java.util.concurrent.CompletableFuture;

public abstract class ValidatorRule implements Rule {

    private final String ruleFormat;
    protected final RulesetRepository rulesetRepository;
    protected final ErrorHandlerService errorHandlerService;

    protected ValidatorRule(
        String ruleFormat,
        RulesetRepository rulesetRepository,
        ErrorHandlerService errorHandlerService) {
        this.ruleFormat = ruleFormat;
        this.rulesetRepository = rulesetRepository;
        this.errorHandlerService = errorHandlerService;
    }

    @Override
    public CompletableFuture<ValidationReport> execute(
        Entry queueEntry,
        PhaseData<FileReferences> phaseData) {

        return CompletableFuture.supplyAsync(() -> {
            if (ruleFormat.equalsIgnoreCase(queueEntry.format())) {
                return runValidator(queueEntry, phaseData);
            } else {
                ImmutableError error = ImmutableError.of(
                    queueEntry.id(),
                    phaseData.phase().id(),
                    rulesetRepository.findByName(getIdentifyingName()).orElseThrow().id(),
                    "Wrong format! Expected '%s', was '%s'".formatted(ruleFormat, queueEntry.format()));
                errorHandlerService.reportError(error);
                // TODO: 'what' obviously needs something better
                return ImmutableValidationReport.of("what").withErrors(error);
            }
        });
    }

    protected abstract ValidationReport runValidator(Entry queueEntry, PhaseData<FileReferences> phaseData);
}
