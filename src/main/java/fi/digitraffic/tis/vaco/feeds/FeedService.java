package fi.digitraffic.tis.vaco.feeds;

import fi.digitraffic.tis.vaco.api.model.feed.CreateFeedRequest;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.FeedRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.FeedRepository;
import fi.digitraffic.tis.vaco.feeds.model.Feed;
import fi.digitraffic.tis.vaco.feeds.model.ImmutableFeed;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static fi.digitraffic.tis.vaco.feeds.model.Feed.FEEDREQUEST_PUBLIC_ID;


@Service
public class FeedService {
    private final FeedRepository feedRepository;
    private final RecordMapper recordMapper;
    private final CompanyRepository companyRepository;

    public FeedService(FeedRepository feedRepository, RecordMapper recordMapper, CompanyRepository companyRepository) {

        this.feedRepository = Objects.requireNonNull(feedRepository);
        this.recordMapper = Objects.requireNonNull(recordMapper);
        this.companyRepository = Objects.requireNonNull(companyRepository);

    }

    public Optional<Feed> createFeed(CreateFeedRequest feed) {

        Optional<CompanyRecord> company = companyRepository.findByBusinessId(feed.owner());
        return company.flatMap(companyRecord -> feedRepository.createFeed(toFeed(feed))
                .map(feedRecord -> recordMapper.toFeed(feedRecord, companyRecord)));
    }


    public List<Feed> listAllFeeds() {
        List<FeedRecord> feedRecords = feedRepository.listAllFeeds();

        return feedRecords.stream()
            .map(feedRecord -> {
               CompanyRecord companyRecord = companyRepository.findById(feedRecord.ownerId());
               return recordMapper.toFeed(feedRecord, companyRecord);

            })
            .toList();
    }

    public boolean deleteFeedByPublicId(String publicId) {
        return feedRepository.deleteByPublicId(publicId);
    }

    public Optional<Feed> modifyFeedProcessing(boolean processingEnabled, String publicId) {

        Optional<FeedRecord> feedRecord = feedRepository.modifyFeedProcessing(processingEnabled, publicId);
        CompanyRecord companyRecord = companyRepository.findById(feedRecord.get().ownerId());

        return Optional.of(recordMapper.toFeed(feedRecord.get(), companyRecord));

    }

    private Feed toFeed(CreateFeedRequest request) {

        Optional<CompanyRecord> company = companyRepository.findByBusinessId(request.owner());

        return ImmutableFeed.builder()
                .publicId(FEEDREQUEST_PUBLIC_ID)
                .owner(recordMapper.toCompany(company.get()))
                .uri(request.uri())
                .format(request.format())
                .processingEnabled(request.processingEnabled())
                .build();
    }

}
