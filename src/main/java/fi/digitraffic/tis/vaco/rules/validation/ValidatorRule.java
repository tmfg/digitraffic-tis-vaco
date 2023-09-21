package fi.digitraffic.tis.vaco.rules.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.vaco.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.ErrorHandlerService;
import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.rules.model.ValidationRuleJobMessage;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import fi.digitraffic.tis.vaco.validation.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.validation.model.ValidationReport;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;

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
    private final PackagesService packagesService;
    private final ObjectMapper objectMapper;

    protected ValidatorRule(String ruleFormat,
                            RulesetRepository rulesetRepository,
                            ErrorHandlerService errorHandlerService,
                            S3Client s3Client,
                            VacoProperties vacoProperties,
                            PackagesService packagesService,
                            ObjectMapper objectMapper) {
        this.ruleFormat = Objects.requireNonNull(ruleFormat);
        this.rulesetRepository = Objects.requireNonNull(rulesetRepository);
        this.errorHandlerService = Objects.requireNonNull(errorHandlerService);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public CompletableFuture<ValidationReport> execute(ValidationRuleJobMessage message) {
        Entry entry = message.entry();
        Task task = message.task();
        S3Path inputsDirectory = S3Path.of(message.inputs());
        S3Path outputsDirectory = S3Path.of(message.outputs());
        Optional<ValidationInput> configuration = Optional.ofNullable(message.configuration());
        Path ruleTempDir = TempFiles.getRuleTempDirectory(vacoProperties, entry, task.name(), getIdentifyingName());

        return CompletableFuture.supplyAsync(() -> {
            if (ruleFormat.equalsIgnoreCase(entry.format())) {
                Path inputDir = ruleTempDir.resolve("input");
                Path outputDir = ruleTempDir.resolve("output");
                ValidationReport report = runValidator(
                    entry,
                    task,
                    downloadFiles(inputDir, entry, task, inputsDirectory),
                    outputDir,
                    configuration);
                //S3Path s3TargetPath = ImmutableS3Path.builder()
                //    .from(S3Artifact.getRuleDirectory(entry.publicId(), task.name(), getIdentifyingName()))
                //    .addPath("output")
                //    .build();
                copyOutputToS3(entry, task, outputDir, outputsDirectory);
                return report;
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

    private Path downloadFiles(Path inputTempDir, Entry entry, Task task, S3Path inputDirectory) {
        CompletedDirectoryDownload x = s3Client.downloadDirectory(
            vacoProperties.getS3ProcessingBucket(),
            inputDirectory,
            inputTempDir
        ).join();
        return inputTempDir;
    }

    private void copyOutputToS3(Entry entry,
                                                Task task,
                                                Path outputDirectory,
                                                S3Path s3TargetPath) {// copy produced output to S3
        CompletedDirectoryUpload ud = s3Client.uploadDirectory(
                outputDirectory,
                vacoProperties.getS3ProcessingBucket(),
                s3TargetPath)
            .join();
        // package and publish all of it
        packagesService.createPackage(entry, task, getIdentifyingName(), s3TargetPath, "content.zip");

        // record failures if any
        Streams.map(ud.failedTransfers(), failure -> {
            ImmutableError error = ImmutableError.of(
                    entry.id(),
                    task.id(),
                    rulesetRepository.findByName(getIdentifyingName()).orElseThrow().id(),
                    "Failed to upload produced output file from %s to S3 %s:%s".formatted(
                        failure.request().source(),
                        failure.request().putObjectRequest().bucket(),
                        failure.request().putObjectRequest().key()))
                .withRaw(objectMapper.valueToTree(failure.exception()));
            errorHandlerService.reportError(error);
            return error;
        }).complete();
    }

    protected abstract ValidationReport runValidator(
        Entry queueEntry,
        Task task,
        Path workDirectory,
        Path outputsDirectory,
        Optional<ValidationInput> configuration);
}
