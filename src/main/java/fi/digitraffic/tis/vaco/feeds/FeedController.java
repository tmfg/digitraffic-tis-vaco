package fi.digitraffic.tis.vaco.feeds;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.feeds.model.Feed;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping({"/v1/feeds", "/feeds"})
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = Objects.requireNonNull(feedService);
    }
    @PostMapping(path = "")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<?> createRealtimeFeed(@RequestBody Feed feed) {
        boolean isRealtime = TransitDataFormat.GBFS.isRealtime(feed.format());

        if (isRealtime) {
            return feedService.createFeed(feed)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.of(Optional.of(feed)));

        } else {
            return Responses.badFeedRequest("format not realtime");
        }

    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<List<Feed>> listFeeds() {
        return ResponseEntity.ok(
            feedService.getAllFeeds());
    }

}
