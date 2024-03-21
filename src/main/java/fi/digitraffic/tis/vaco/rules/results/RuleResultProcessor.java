package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.vaco.VacoException;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.Finding;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.findings.FindingSeverity;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class RuleResultProcessor implements ResultProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PackagesService packagesService;
    private final S3Client s3Client;
    private final TaskService taskService;
    private final FindingService findingService;
    private final VacoProperties vacoProperties;

    protected RuleResultProcessor(VacoProperties vacoProperties,
                                  PackagesService packagesService,
                                  S3Client s3Client,
                                  TaskService taskService,
                                  FindingService findingService) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.taskService = Objects.requireNonNull(taskService);
        this.findingService = Objects.requireNonNull(findingService);
    }

    /**
     * Truncate output filenames to contain only the file name without path. Assumes results are in flat directory.
     *
     * @param resultMessage Message to get the file names from.
     * @return Map of file names without directory prefixes to full file name.
     */
    protected static Map<String, String> collectOutputFileNames(ResultMessage resultMessage) {
        return Streams.collect(
            resultMessage.uploadedFiles().keySet(),
            m -> m.substring(m.lastIndexOf('/') + 1),
            Function.identity());
    }

    protected void createOutputPackages(ResultMessage resultMessage, Entry entry, Task task) {
        // package generation based on rule outputs
        ConcurrentMap<String, List<String>> packagesToCreate = collectPackageContents(resultMessage.uploadedFiles());

        packagesToCreate.forEach((packageName, files) -> {
            logger.info("Creating package '{}' with files {}", packageName, files);
            packagesService.createPackage(
                entry,
                task,
                packageName,
                S3Path.of(URI.create(resultMessage.outputs()).getPath()), packageName + ".zip",
                file -> {
                    boolean match = files.stream().anyMatch(content -> content.endsWith(file));
                    logger.trace("Matching {} / {}", file, match);
                    return match;
                });
        });
    }

    protected ConcurrentMap<String, List<String>> collectPackageContents(Map<String, List<String>> uploadedFiles) {
        ConcurrentMap<String, List<String>> packagesToCreate = new ConcurrentHashMap<>();
        uploadedFiles.forEach((file, packages) -> {
            for (String p : packages) {
                packagesToCreate.computeIfAbsent(p, k -> new ArrayList<>()).add(file);
            }
        });
        return packagesToCreate;
    }

    protected Path downloadFile(Map<String, String> fileNames, String fileName, Path outputDir) {
        Path reportFile = outputDir.resolve(fileName);
        URI s3Uri = URI.create(fileNames.get(fileName));
        s3Client.downloadFile(s3Uri.getHost(), S3Path.of(s3Uri.getPath()), reportFile);
        return reportFile;
    }

    protected void resolveTaskStatus(Entry entry, Task task) {
    Map<String, Long> severities = findingService.summarizeFindingsSeverities(entry, task);
        logger.debug("{}/{} produced findings {}", entry.publicId(), task.name(), severities);
        if (severities.getOrDefault(FindingSeverity.ERROR, 0L) > 0
            || severities.getOrDefault(FindingSeverity.CRITICAL, 0L) > 0) {
            taskService.markStatus(entry, task, Status.ERRORS);
        } else if (severities.getOrDefault(FindingSeverity.WARNING, 0L) > 0) {
            taskService.markStatus(entry, task, Status.WARNINGS);
        } else {
            taskService.markStatus(entry, task, Status.SUCCESS);
        }
    }

    protected boolean storeFindings(List<Finding> findings) {
        return findingService.reportFindings(findings);
    }

    protected boolean processFile(ResultMessage resultMessage,
                                  Entry entry,
                                  Task task,
                                  Map<String, String> fileNames,
                                  String fileName,
                                  Predicate<Path> consumeFile) {
        if (fileNames.containsKey(fileName)) {
            try {
                Path ruleTemp = TempFiles.getRuleTempDirectory(vacoProperties, entry, task.name(), resultMessage.ruleName());
                Path outputDir = ruleTemp.resolve("output");

                Path reportsFile = downloadFile(fileNames, fileName, outputDir);

                return consumeFile.test(reportsFile);
            } catch (VacoException e) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to process file %s of %s/%s".formatted(fileName, entry.publicId(), task.name()), e);
                }
                return false;
            }
        } else {
            logger.warn("Expected file '{}' missing from output for message {}", fileName, resultMessage);
            return false;
        }
    }
}
