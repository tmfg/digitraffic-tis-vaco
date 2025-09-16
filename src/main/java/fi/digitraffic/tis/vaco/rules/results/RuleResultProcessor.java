package fi.digitraffic.tis.vaco.rules.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.ImmutableS3Path;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.VacoException;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.UnknownEntityException;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.findings.model.FindingSeverity;
import fi.digitraffic.tis.vaco.findings.model.ImmutableFinding;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class RuleResultProcessor implements ResultProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PackagesService packagesService;
    private final S3Client s3Client;
    private final TaskService taskService;
    private final FindingService findingService;
    private final VacoProperties vacoProperties;

    private final ObjectMapper objectMapper;

    private final RulesetService rulesetService;

    protected RuleResultProcessor(VacoProperties vacoProperties,
                                  PackagesService packagesService,
                                  S3Client s3Client,
                                  TaskService taskService,
                                  FindingService findingService,
                                  RulesetService rulesetService,
                                  ObjectMapper objectMapper) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.packagesService = Objects.requireNonNull(packagesService);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.taskService = Objects.requireNonNull(taskService);
        this.findingService = Objects.requireNonNull(findingService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public boolean processResults(ResultMessage resultMessage, Entry entry, Task task) {
        Map<String, String> fileNames = collectOutputFileNames(resultMessage);
        scanDebugLogs(resultMessage, entry, task, fileNames);
        return doProcessResults(resultMessage, entry, task, fileNames);
    }

    private void scanDebugLogs(ResultMessage resultMessage, Entry entry, Task task, Map<String, String> fileNames) {
        processLog(resultMessage, entry, task, fileNames, "stderr.log", "Exception");
        processLog(resultMessage, entry, task, fileNames, "stdout.log", "Exception");

    }

    abstract boolean doProcessResults(ResultMessage resultMessage, Entry entry, Task task, Map<String, String> fileNames);

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

    protected Set<String> createOutputPackages(ResultMessage resultMessage, Entry entry, Task task, Set<String> requiredFiles) {
        // package generation based on rule outputs
        ConcurrentMap<String, List<String>> packagesToCreate = collectPackageContents(resultMessage.uploadedFiles());

        packagesToCreate.forEach((packageName, files) -> createOutputPackage(entry, task, packageName, files));
        Set<String> uploadedFileNames = new HashSet<>();

        for (String url : resultMessage.uploadedFiles().keySet()) {
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            uploadedFileNames.add(fileName);
        }

        packagesToCreate.forEach((packageName, files) -> createOutputPackage(entry, task, packageName, files));

        return requiredFiles.stream().filter(f -> !uploadedFileNames.contains(f)).collect(Collectors.toSet());
    }

    protected void createOutputPackage(Entry entry, Task task, String packageName, List<String> files) {
        logger.info("Creating package '{}' with files {}", packageName, files);
        packagesService.createPackage(
            entry,
            task,
            packageName,
            ImmutableS3Path.of(List.of(entry.publicId(), Objects.requireNonNull(task.publicId()))),
            packageName + ".zip",
            file -> {
                boolean match = files.stream().anyMatch(content -> content.endsWith(file));
                logger.trace("Matching {} / {}", file, match);
                return match;
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
        resolveTaskStatus(entry, task, Optional.empty());
    }

    protected void resolveTaskStatus(Entry entry, Task task, Optional<Status> override) {
        Map<String, Long> severities = findingService.summarizeFindingsSeverities(task);
        logger.debug("{}/{} ({}) produced findings {}", entry.publicId(), task.name(), task.publicId(), severities);

        if (override.isPresent()) {
            taskService.markStatus(entry, task, override.get());
        } else {
            if (severities.getOrDefault(FindingSeverity.ERROR, 0L) > 0
                || severities.getOrDefault(FindingSeverity.CRITICAL, 0L) > 0) {
                taskService.markStatus(entry, task, Status.ERRORS);
            } else if (severities.getOrDefault(FindingSeverity.WARNING, 0L) > 0) {
                taskService.markStatus(entry, task, Status.WARNINGS);
            } else if (severities.getOrDefault(FindingSeverity.FAILURE, 0L) > 0) {
                taskService.markStatus(entry, task, Status.FAILED);
            } else {
                taskService.markStatus(entry, task, Status.SUCCESS);
            }
        }

        taskService.trackTask(entry, task, ProcessingState.COMPLETE);
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

    protected List<ImmutableFinding> scanErrorLog(Entry entry, Task task, String ruleName, Path reportsFile, String errorMarker) throws IOException {

        try {
            Long rulesetId = rulesetService.findByName(ruleName)
                .orElseThrow(() -> new UnknownEntityException(ruleName, "Unknown rule name"))
                .id();
            try (Stream<String> reportLines = Files.lines(reportsFile)) {

                return reportLines.map(errorLine -> {
                        if (errorLine.contains(errorMarker)) {
                            String errorDetail = errorLine.substring(errorLine.indexOf(":") + 1).trim();
                            Map<String, String> errorMap = Map.of(errorMarker, errorDetail);
                            try {
                                return ImmutableFinding.of(
                                        task.id(),
                                        rulesetId,
                                        ruleName,
                                        errorMarker,
                                        FindingSeverity.FAILURE
                                    )
                                    .withRaw(objectMapper.writeValueAsBytes(errorMap));
                            } catch (JsonProcessingException e) {
                                logger.warn("Failed to convert tree to bytes", e);
                                return null;
                            }

                        } else {
                            return null;
                        }

                    })
                    .filter(Objects::nonNull)
                    .toList();
            }

        } catch (IOException e) {
            logger.warn("Failed to process {}/{}/{} output file", entry.publicId(), task.name(), ruleName, e);
            return List.of();
        }
    }


    protected boolean processLog(ResultMessage resultMessage,
                                 Entry entry,
                                 Task task,
                                 Map<String, String> fileNames,
                                 String fileName,
                                 String errorMarker) {
        return processFile(resultMessage, entry, task, fileNames, fileName, path -> {
            List<Finding> findings;
            try {
                findings = new ArrayList<>(scanErrorLog(entry, task, resultMessage.ruleName(), path, errorMarker));
                return storeFindings(findings);
            } catch (IOException e) {
                throw new RuleExecutionException("Stdout.log file could not be read into findings in entry " + entry.publicId());
            }
        });
    }

    protected List<ImmutableFinding> scanLog(Entry entry, Task task, String ruleName, Path reportsFile, String errorMarker) throws IOException {

        try {
            Long rulesetId = rulesetService.findByName(ruleName)
                .orElseThrow(() -> new UnknownEntityException(ruleName, "Unknown rule name"))
                .id();
            try (Stream<String> reportLines = Files.lines(reportsFile)) {

                return reportLines.map(errorLine -> {
                        if (errorLine.startsWith(errorMarker)) {
                            String errorDetail = errorLine.substring(errorLine.indexOf(":") + 1).trim();
                            Map<String, String> errorMap = new HashMap<>();
                            errorMap.put(errorMarker, errorDetail);
                            try {
                                return ImmutableFinding.of(
                                        task.id(),
                                        rulesetId,
                                        ruleName,
                                        errorMarker,
                                        FindingSeverity.ERROR
                                    )
                                    .withRaw(objectMapper.writeValueAsBytes(errorMap));
                            } catch (JsonProcessingException e) {
                                logger.warn("Failed to convert tree to bytes", e);
                                return null;
                            }

                        } else {
                            return null;
                        }

                    })
                    .filter(Objects::nonNull)
                    .toList();
            }

        } catch (IOException e) {
            logger.warn("Failed to process {}/{}/{} output file", entry.publicId(), task.name(), ruleName, e);
            return List.of();
        }
    }

    protected void requiredFilesNotFound(Entry entry, Task task, Set<String> filesFound){

        Finding finding = createFinding(entry, task, filesFound);
        findingService.reportFinding(finding);
        Optional<Status> status = Optional.of(Status.ERRORS);
        resolveTaskStatus(entry, task, status);
        logger.warn("All required files not found {}, {}", entry, task);
    }

    protected Finding createFinding(Entry entry, Task task, Set<String> filesFound){

        String findingRaw = "{\"Not all needed files were found, missing file:\":\"" + filesFound +" \"}";
        byte[] raw = findingRaw.getBytes(StandardCharsets.UTF_8);

        return ImmutableFinding.builder()
            .publicId(entry.publicId())
            .taskId(task.id())
            .source(task.name())
            .message("All required files not found")
            .severity(FindingSeverity.ERROR)
            .raw(raw)
            .build();

    }
}
