package fi.digitraffic.tis.vaco.statistics;

import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.StatusStatisticsRecord;
import fi.digitraffic.tis.vaco.db.repositories.StatisticsRepository;
import fi.digitraffic.tis.vaco.statistics.model.StatusStatistics;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatisticsService {

    private final StatisticsRepository statisticsRepository;
    private final RecordMapper recordMapper;
    private StatisticsService(StatisticsRepository statisticsRepository, RecordMapper recordMapper) {
        this.statisticsRepository = statisticsRepository;
        this.recordMapper = recordMapper;
    }

    public void refreshMaterializedView() {
        statisticsRepository.refreshView();
    }

    public List<StatusStatistics> fetchAllStatistics() {
        return statisticsRepository.listStatusStatistics().stream()
            .map(recordMapper::toStatusStatistics).toList();

    }
}
