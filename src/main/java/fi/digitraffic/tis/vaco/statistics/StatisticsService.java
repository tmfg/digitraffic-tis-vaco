package fi.digitraffic.tis.vaco.statistics;

import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.repositories.StatisticsRepository;
import fi.digitraffic.tis.vaco.statistics.model.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class StatisticsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StatisticsRepository statisticsRepository;
    private final RecordMapper recordMapper;

    public StatisticsService(StatisticsRepository statisticsRepository,
                              RecordMapper recordMapper) {
        this.statisticsRepository = Objects.requireNonNull(statisticsRepository);
        this.recordMapper = Objects.requireNonNull(recordMapper);
    }

    @Scheduled(cron = "${vaco.scheduling.refresh-statistics.cron}")
    public void dailyStatistics() {
        try {
            refreshMaterializedView();
        } catch (Exception e) {
            logger.warn("Failed to refresh status views ", e);
        }
    }

    public void refreshMaterializedView() {
        statisticsRepository.refreshView();
    }

    public List<Statistics> fetchAllEntryStatistics() {
        return statisticsRepository.listEntryStatusStatistics().stream()
            .map(recordMapper::toStatusStatistics).toList();

    }

    public List<Statistics> fetchAllTaskStatistics() {
        return statisticsRepository.listTaskStatusStatistics().stream()
            .map(recordMapper::toStatusStatistics).toList();

    }
}
