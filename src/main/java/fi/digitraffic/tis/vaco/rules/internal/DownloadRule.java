package fi.digitraffic.tis.vaco.rules.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.utilities.Archiver;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.TempFiles;
import fi.digitraffic.tis.utilities.model.ProcessingState;
import fi.digitraffic.tis.vaco.aws.S3Artifact;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.findings.model.ImmutableFinding;
import fi.digitraffic.tis.vaco.http.VacoHttpClient;
import fi.digitraffic.tis.vaco.http.model.DownloadResponse;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.Rule;
import fi.digitraffic.tis.vaco.rules.RuleExecutionException;
import fi.digitraffic.tis.vaco.rules.model.ImmutableResultMessage;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.rules.model.gbfs.Discovery;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

@Component
public class DownloadRule implements Rule<Entry, ResultMessage> {
    public static final String PREPARE_DOWNLOAD_TASK = "prepare.download";
    private final EntryService entryService;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ObjectMapper objectMapper;
    private final TaskService taskService;
    private final VacoProperties vacoProperties;
    private final VacoHttpClient httpClient;
    private final S3Client s3Client;
    private final FindingService findingService;

    public DownloadRule(ObjectMapper objectMapper, TaskService taskService,
                        VacoProperties vacoProperties,
                        VacoHttpClient httpClient,
                        S3Client s3Client,
                        FindingService findingService, EntryService entryService) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.taskService = Objects.requireNonNull(taskService);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.findingService = Objects.requireNonNull(findingService);
        this.entryService = entryService;
    }

    @Override
    public CompletableFuture<ResultMessage> execute(Entry entry) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Task> task = taskService.findTask(entry.publicId(), PREPARE_DOWNLOAD_TASK);
            return task.map(t -> {
                Task tracked = taskService.trackTask(entry, t, ProcessingState.START);
                Path tempDirPath = TempFiles.getTaskTempDirectory(vacoProperties, entry, tracked);

                try {
                    S3Path ruleBasePath = S3Artifact.getRuleDirectory(entry.publicId(), PREPARE_DOWNLOAD_TASK, PREPARE_DOWNLOAD_TASK);
                    S3Path ruleS3Input = ruleBasePath.resolve("input");
                    S3Path ruleS3Output = ruleBasePath.resolve("output");

                    // download: maybe fetch files and validate it
                    Optional<S3Path> result = download(entry, tempDirPath, tracked, ruleS3Output);

                    // inspect result: did download result in file(s) in S3?
                    String downloadedFilePackage = "result";
                    Map<String, List<String>> uploadedFiles = Map.of();
                    if (result.isPresent()) {
                        taskService.markStatus(entry, tracked, Status.SUCCESS);
                        S3Path s3Path = result.get();
                        uploadedFiles = Map.of(s3Path.asUri(vacoProperties.s3ProcessingBucket()), List.of(downloadedFilePackage));
                    } else {
                        // did not produce file but that might be intended (e.g. ETag results in 304), cancel task
                        taskService.markStatus(entry, tracked, Status.CANCELLED);
                    }

                    // generic response:
                    return ImmutableResultMessage.builder()
                        .entryId(entry.publicId())
                        .taskId(tracked.id())
                        .ruleName(PREPARE_DOWNLOAD_TASK)
                        .inputs(ruleS3Input.asUri(vacoProperties.s3ProcessingBucket()))
                        .outputs(ruleS3Output.asUri(vacoProperties.s3ProcessingBucket()))
                        .uploadedFiles(uploadedFiles)
                        .build();
                }  catch (Exception e) {
                    logger.warn("Caught unrecoverable exception during file download", e);
                    taskService.markStatus(entry, tracked, Status.FAILED);
                    return null;
                } finally {
                    try (Stream<Path> tempFiles = Files.walk(tempDirPath)) {
                        tempFiles.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                    /* ignored on purpose */
                                }
                            });
                    } catch (IOException ignored) {
                        /* ignored on purpose */
                    }
                }
            }).orElseThrow();
        });
    }

    private Optional<S3Path> download(Entry entry, Path tempDirPath, Task tracked, S3Path ruleS3Output) {
        if (shouldDownload(entry)) {
            CompletableFuture<Optional<Path>> feedArchive;

            if (TransitDataFormat.GBFS.fieldName().equalsIgnoreCase(entry.format())) {
                // GBFS feed is a directive pointing out to more files, so need to download them all separately and build a ZIP
                feedArchive = httpClient.downloadFile(
                        TempFiles.getTaskTempFile(tempDirPath, entry.format() + ".json"),
                        entry.url(),
                        entry.etag())
                    .thenApply(updateEtag(entry))
                    .thenCompose(downloadGbfsDiscovery(entry, tempDirPath));
            } else {
                // by default we assume single ZIP files, this applies to e.g. GTFS and NeTEx
                feedArchive = httpClient.downloadFile(
                    TempFiles.getTaskTempFile(tempDirPath, entry.format() + ".zip"),
                    entry.url(),
                    entry.etag())
                    .thenApply(updateEtag(entry))
                    .thenApply(DownloadResponse::body);
            }

            return feedArchive.thenCompose(validateZip(entry, tracked))
                .thenApply(track(entry, tracked, ProcessingState.UPDATE))
                .thenCompose(uploadToS3(entry, ruleS3Output, tracked))
                .thenApply(track(entry, tracked, ProcessingState.COMPLETE))
                .join();
        } else {
            // yes, this looks a bit weird, it's because it reuses the function from above composition
            track(entry, tracked, ProcessingState.COMPLETE).apply(tracked);
            return Optional.empty();
        }
    }

    /**
     * Update current entry's etag if one was present in download to allow detection of stale/updated etags for further
     * runs based on context link, if any.
     *
     * @param entry Entry to update.
     * @return the same response, unmodified
     */
    private Function<DownloadResponse, DownloadResponse> updateEtag(Entry entry) {
        return downloadResponse -> {
            downloadResponse.etag().ifPresent(etag -> entryService.updateEtag(entry, etag));
            return downloadResponse;
        };
    }

    private boolean shouldDownload(Entry entry) {
        if (entry.context() != null && entry.etag() != null) {
            Optional<Entry> previousEntry = entryService.findLatestEntryForContext(entry.businessId(), entry.context());
            if (previousEntry.isPresent() && previousEntry.get().etag().equals(entry.etag())) {
                logger.info("Entry {} with context '{}' has same ETag '{}' as its predecessor, skipping download", entry.businessId(), entry.context(), entry.etag());
                return false;
            }
        }
        return true;
    }

    private Function<DownloadResponse, CompletionStage<Optional<Path>>> downloadGbfsDiscovery(Entry entry, Path tempDirPath) {
        return discoveryFile -> {
            if (discoveryFile.body().isPresent()) {
                try {
                    Path discoveryDir = Files.createDirectories(tempDirPath.resolve("discovery"));
                    Discovery discovery = objectMapper.readValue(discoveryFile.body().get().toFile(), Discovery.class);

                    List<CompletableFuture<DownloadResponse>> contents = Streams.flatten(discovery.data().values(), Map::values)
                        .flatten(Function.identity())
                        .map(feed -> httpClient.downloadFile(
                            TempFiles.getTaskTempFile(discoveryDir, feed.name() + ".json"),
                            feed.url(),
                            null))
                        .filter(Objects::nonNull)
                        .toList();

                    CompletableFuture<Void> all = CompletableFuture.allOf(contents.toArray(new CompletableFuture[0]));
                    return all.thenApply(v -> {
                        Path finalFile = TempFiles.getTaskTempFile(tempDirPath, entry.format() + ".zip");
                        try {
                            Archiver.createZip(discoveryDir, finalFile);
                        } catch (IOException e) {
                            throw new RuleExecutionException("Could not create ZIP archive for GBFS content", e);
                        }
                        return Optional.of(finalFile);
                    });
                } catch (IOException e) {
                    logger.warn("Failed to deserialize assumed GBFS content", e);
                    return CompletableFuture.completedFuture(Optional.empty());
                }
            } else {
                logger.info("GBFS discovery file not present");
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
    }

    private <T> Function<T, T> track(Entry entry, Task task, ProcessingState state) {
        return t -> {
            taskService.trackTask(entry, task, state);
            return t;
        };
    }

    /**
     * Ensure the downloaded file is a valid ZIP file, e.g. not partial, corrupted or complete nonsense.
     * <p>
     * Original from <a href="https://stackoverflow.com/a/17222756/44523">StackOverflow</a> by Nikhil Das Nomula,
     * licensed under CC BY-SA 3.0.
     *
     * @return Composable function which returns the input as is if it represents a valid ZIP file, empty otherwise
     */
    private Function<Optional<Path>, CompletableFuture<Optional<Path>>> validateZip(Entry entry, Task task) {
        return path -> {
            if (path.isPresent()) {
                File file = path.get().toFile();

                try (ZipFile zipfile = new ZipFile(file)) {
                    ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
                    ZipEntry ze = zis.getNextEntry();
                    if (ze == null) {
                        logger.warn("Entry {} processed ZIP file at path {} is empty", entry.publicId(), file);
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                    while (ze != null) {
                        // if it throws an exception fetching any of the following then we know the file is corrupted.
                        zipfile.getInputStream(ze);
                        ze.getCrc();
                        ze.getCompressedSize();
                        ze.getName();
                        ze = zis.getNextEntry();
                    }
                    return CompletableFuture.completedFuture(path);
                } catch (IOException e) {
                    findingService.reportFinding(ImmutableFinding.of(entry.publicId(), task.id(), null, PREPARE_DOWNLOAD_TASK, e.getMessage(), "ERROR"));
                    return CompletableFuture.completedFuture(Optional.empty());
                }
            } else {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
    }

    private Function<Optional<Path>, CompletableFuture<Optional<S3Path>>> uploadToS3(Entry entry,
                                                                                     S3Path outputDir,
                                                                                     Task task) {
        return path -> {
            if (path.isPresent()) {
                Path localPath = path.get();
                S3Path s3TargetPath = outputDir.resolve(entry.format() + ".zip");

                return s3Client.uploadFile(vacoProperties.s3ProcessingBucket(), s3TargetPath, localPath)
                    .thenApply(track(entry, task, ProcessingState.UPDATE))
                    .thenApply(u -> Optional.of(s3TargetPath));  // NOTE: There's probably something useful in the `u` parameter
            } else {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
    }
}
