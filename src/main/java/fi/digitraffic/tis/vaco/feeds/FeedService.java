package fi.digitraffic.tis.vaco.feeds;

import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.FeedRecord;
import fi.digitraffic.tis.vaco.db.repositories.FeedRepository;
import fi.digitraffic.tis.vaco.feeds.model.Feed;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


@Service
public class FeedService {
    private final FeedRepository feedRepository;
    private final RecordMapper recordMapper;
    public FeedService(FeedRepository feedRepository, RecordMapper recordMapper) {

        this.feedRepository = feedRepository;
        this.recordMapper = recordMapper;
    }

    public Optional<Feed> createFeed(Feed feed) {
        return feedRepository.createFeed(feed)
            .map(recordMapper::toFeed);
    }

    public List<Feed> getAllFeeds() {
        List<FeedRecord> feeds = feedRepository.getAllFeeds();
        return feeds.stream()
            .map(recordMapper::toFeed)
            .toList();

    }
}
