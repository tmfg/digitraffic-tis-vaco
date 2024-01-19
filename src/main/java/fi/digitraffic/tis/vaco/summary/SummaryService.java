package fi.digitraffic.tis.vaco.summary;

import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@Service
public class SummaryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PackagesService packagesService;
    private final GtfsInputSummaryService gtfsInputSummaryService;
    private final NetexInputSummaryService netexInputSummaryService;

    public SummaryService(PackagesService packagesService,
                          GtfsInputSummaryService gtfsInputSummaryService,
                          NetexInputSummaryService netexInputSummaryService) {
        this.packagesService = packagesService;
        this.gtfsInputSummaryService = gtfsInputSummaryService;
        this.netexInputSummaryService = netexInputSummaryService;
    }

    public void generateSummaries(Entry entry, Task task) {
        if (DownloadRule.DOWNLOAD_SUBTASK.equals(task.name())) {
            generateInputDataSummaries(entry, task);
        }
    }

    private void generateInputDataSummaries(Entry entry, Task task) {
        Optional<Path> downloadedPackagePath = packagesService.downloadPackage(entry, task, "result");
        if (downloadedPackagePath.isEmpty()) {
            logger.error("Failed to generate input data summaries while trying to get S3 path to entry's '{}' downloaded data package", entry.id());
            return;
        }

        if (TransitDataFormat.GTFS.fieldName().equals(entry.format())) {
            try {
                gtfsInputSummaryService.generateGtfsInputSummaries(downloadedPackagePath.get(), task.id());
            } catch (IOException e) {
                logger.error("Failed to generate GTFS input data summaries for entry {}", entry.id(), e);
            }
        } else if (TransitDataFormat.NETEX.fieldName().equals(entry.format())) {
            try {
                netexInputSummaryService.generateNetexInputSummaries(downloadedPackagePath.get(), task.id());
            } catch (IOException e) {
                logger.error("Failed to generate NeTEx input data summaries for entry {}", entry.id(), e);
            }
        }
    }
}
