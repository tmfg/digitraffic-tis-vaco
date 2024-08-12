package fi.digitraffic.tis.vaco.rules.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.aws.s3.S3Client;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.UnknownEntityException;
import fi.digitraffic.tis.vaco.findings.FindingService;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.findings.model.ImmutableFinding;
import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class GtfsToNetexResultProcessor extends RuleResultProcessor implements ResultProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final RulesetService rulesetService;
    private final ObjectMapper objectMapper;

    protected GtfsToNetexResultProcessor(VacoProperties vacoProperties,
                                         PackagesService packagesService,
                                         S3Client s3Client,
                                         TaskService taskService,
                                         RulesetService rulesetService,
                                         FindingService findingService,
                                         ObjectMapper objectMapper) {
        super(vacoProperties, packagesService, s3Client, taskService, findingService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public boolean processResults(ResultMessage resultMessage, Entry entry, Task task) {

        createOutputPackages(resultMessage, entry, task);

        Map<String, String> fileNames = collectOutputFileNames(resultMessage);

        boolean errorsProcessed = processFile(resultMessage, entry, task, fileNames, "errors.json", path -> {
            List<Finding> findings = new ArrayList<>(scanErrorFile(entry, task, resultMessage.ruleName(), path));
            return storeFindings(findings);
        });

        resolveTaskStatus(entry, task);

        return errorsProcessed;
    }

    private List<ImmutableFinding> scanErrorFile(Entry entry, Task task, String ruleName, Path reportsFile) {
        try {
            Long rulesetId = rulesetService.findByName(ruleName)
                .orElseThrow(() -> new UnknownEntityException(ruleName, "Unknown rule name"))
                .id();
            List<Map<String, Object>> errorList = objectMapper.readValue(reportsFile.toFile(), new TypeReference<>() {
            });
            return errorList.stream()
                .map(errorMap -> {
                    String errorMsg = (String) errorMap.get("errorMsg");
                    if (errorMsg == null) {
                        return null;
                    }
                        try {
                            return ImmutableFinding.of(
                                    entry.publicId(),
                                    task.id(),
                                    rulesetId,
                                    ruleName,
                                    errorMsg,
                                    "ERROR"
                                    )
                                .withRaw(objectMapper.writeValueAsBytes(errorMap));
                        } catch (JsonProcessingException e) {
                            logger.warn("Failed to convert tree to bytes", e);
                            return null;
                        }
                    })
                .filter(Objects::nonNull)
                .toList();
        } catch (IOException e) {
            logger.warn("Failed to process {}/{}/{} output file", entry.publicId(), task.name(), ruleName, e);
            return List.of();
        }
    }
}

