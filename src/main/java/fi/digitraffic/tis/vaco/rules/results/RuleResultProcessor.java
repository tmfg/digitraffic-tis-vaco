package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.aws.s3.S3Path;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class RuleResultProcessor implements ResultProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PackagesService packagesService;

    protected RuleResultProcessor(PackagesService packagesService) {
        this.packagesService = Objects.requireNonNull(packagesService);
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
}
