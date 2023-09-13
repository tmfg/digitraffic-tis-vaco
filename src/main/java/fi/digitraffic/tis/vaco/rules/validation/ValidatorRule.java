package fi.digitraffic.tis.vaco.rules.validation;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryDownload;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class ValidatorRule implements Rule<ValidationInput, ValidationReport> {

    private final String ruleFormat;
    protected final RulesetRepository rulesetRepository;
    protected final ErrorHandlerService errorHandlerService;
    private final S3Client s3Client;
    private final VacoProperties vacoProperties;

    protected ValidatorRule(
        String ruleFormat,
        RulesetRepository rulesetRepository,
        ErrorHandlerService errorHandlerService, S3Client s3Client, VacoProperties vacoProperties) {
        this.ruleFormat = Objects.requireNonNull(ruleFormat);
        this.rulesetRepository = Objects.requireNonNull(rulesetRepository);
        this.errorHandlerService = Objects.requireNonNull(errorHandlerService);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
    }

    @Override
    public CompletableFuture<ValidationReport> execute(Entry entry,
                                                       Task task,
                                                       S3Path inputDirectory,
                                                       Optional<ValidationInput> configuration) {
        return CompletableFuture.supplyAsync(() -> {
            if (ruleFormat.equalsIgnoreCase(entry.format())) {
                return runValidator(entry, task, downloadFiles(entry, task, inputDirectory), configuration);
            } else {
                ImmutableError error = ImmutableError.of(
                    entry.id(),
                    task.id(),
                    rulesetRepository.findByName(getIdentifyingName()).orElseThrow().id(),
                    "Wrong format! Expected '%s', was '%s'".formatted(ruleFormat, entry.format()));
                errorHandlerService.reportError(error);
                // TODO: 'what' obviously needs something better
                return ImmutableValidationReport.of("what").withErrors(error);
            }
        });
    }

    private Path downloadFiles(Entry entry, Task task, S3Path inputDirectory) {
        Path ruleTempDir = TempFiles.getRuleTempDirectory(vacoProperties, entry.publicId(), task.name(), getIdentifyingName());
        CompletedDirectoryDownload x = s3Client.downloadDirectory(
            vacoProperties.getS3ProcessingBucket(),
            inputDirectory,
            ruleTempDir.resolve("input")
        ).join();
        return ruleTempDir;
    }

    protected abstract ValidationReport runValidator(
        Entry queueEntry,
        Task task,
        Path workDirectory,
        Optional<ValidationInput> configuration);
}
