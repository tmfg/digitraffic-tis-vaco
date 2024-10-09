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
import fi.digitraffic.tis.vaco.summary.GtfsInputSummaryService;
import fi.digitraffic.tis.vaco.summary.model.RendererType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class GtfsToNetexResultProcessor extends RuleResultProcessor implements ResultProcessor {

    public static final String STATS_JSON = "_stats.json";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final RulesetService rulesetService;
    private final ObjectMapper objectMapper;
    private final GtfsInputSummaryService gtfsInputSummaryService;

    public GtfsToNetexResultProcessor(VacoProperties vacoProperties,
                                      PackagesService packagesService,
                                      S3Client s3Client,
                                      TaskService taskService,
                                      RulesetService rulesetService,
                                      FindingService findingService,
                                      ObjectMapper objectMapper, GtfsInputSummaryService gtfsInputSummaryService) {
        super(vacoProperties, packagesService, s3Client, taskService, findingService);
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.gtfsInputSummaryService = Objects.requireNonNull(gtfsInputSummaryService);
    }

    @Override
    public boolean processResults(ResultMessage resultMessage, Entry entry, Task task) {

        createOutputPackages(resultMessage, entry, task);

        Map<String, String> fileNames = collectOutputFileNames(resultMessage);

        boolean statsProcessed = fileNames.keySet().stream()
            .filter(key -> key.endsWith(STATS_JSON))
            .findFirst()
            .map(key -> processFile(resultMessage, entry, task, fileNames, key, path -> {
                try {
                    ArrayList<String> gtfs2netexStats = scanStatsFileToArray(path);
                    return saveGtfs2NetexSummary(task, gtfs2netexStats);
                } catch (JSONException | IOException e) {
                    throw new RuntimeException(e);
                }
            }))
            .orElse(false);

        boolean errorsProcessed = processFile(resultMessage, entry, task, fileNames, "errors.json", path -> {
            List<Finding> findings = new ArrayList<>(scanErrorFile(entry, task, resultMessage.ruleName(), path));
            return storeFindings(findings);
        });

        resolveTaskStatus(entry, task);

        return errorsProcessed && statsProcessed;
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

    private ArrayList<String> scanStatsFileToArray(Path reportsFile) throws JSONException, IOException {
        Map<String, Integer> statsListFile = objectMapper.readValue(reportsFile.toFile(), new TypeReference<>() {});

        JSONObject statsObject = new JSONObject(statsListFile);

        ArrayList<String> statsList = new ArrayList<>();

        Iterator<String> keys = statsObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = key + ": " + statsObject.get(key);
            statsList.add(value);
        }

        return statsList;
    }

    private boolean saveGtfs2NetexSummary(Task task, ArrayList<String> gtfs2netexStats) {
        gtfsInputSummaryService.persistTaskSummaryItem(task, "counts", RendererType.LIST, gtfs2netexStats);
        return true;
    }
}

