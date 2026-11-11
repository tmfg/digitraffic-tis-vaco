package fi.digitraffic.tis.vaco.summary;

import fi.digitraffic.tis.vaco.db.repositories.SummaryRepository;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.summary.model.ImmutableSummary;
import fi.digitraffic.tis.vaco.summary.model.RendererType;
import fi.digitraffic.tis.vaco.summary.model.ssim.SsimSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@Service
public class SsimSummaryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;

    private final SummaryRepository summaryRepository;

    public SsimSummaryService(SummaryRepository summaryRepository,
                                   ObjectMapper objectMapper) {
        this.summaryRepository = Objects.requireNonNull(summaryRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public void generateSsimInputSummaries(Entry entry, Task task, Path downloadedSummaryFilePath) {
        logger.info("Starting SSIM input summary generation for entry {} task {}", entry.publicId(), task.name());
        try {
            SsimSummary ssimSummary = objectMapper.readValue(downloadedSummaryFilePath.toFile(), SsimSummary.class);
            persistTaskSummaryItem(task, "feedInfo", RendererType.TABULAR, ssimSummary.feedInfo());
            persistTaskSummaryItem(task, "airlines", RendererType.CARD, ssimSummary.airlines());
            persistTaskSummaryItem(task, "airports", RendererType.CARD, ssimSummary.airports());
            persistTaskSummaryItem(task, "counts", RendererType.LIST, ssimSummary.counts());
        } catch (JacksonException e) {
            logger.warn("Failed to parse SSIM summary from {}", downloadedSummaryFilePath, e);
            throw e;
        }
    }

    public  <T> void persistTaskSummaryItem(Task task, String itemName, RendererType rendererType, T data) {
        try {
            summaryRepository.create(ImmutableSummary.of(task.id(), itemName, rendererType, objectMapper.writeValueAsBytes(data)));
        }
        catch (JacksonException e) {
            logger.error("Failed to persist {}'s summary data {} generated for task {}", itemName, data, task.name(), e);
        }
    }
}
