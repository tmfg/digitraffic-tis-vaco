package fi.digitraffic.tis.vaco.feeds;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.api.model.feed.CreateFeedRequest;
import fi.digitraffic.tis.vaco.feeds.model.Feed;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping({"/v1/feeds", "/feeds"})
public class FeedController {

    private final FeedService feedService;
    public FeedController(FeedService feedService) {
        this.feedService = Objects.requireNonNull(feedService);
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<Feed>> createFeed(@Valid @RequestBody CreateFeedRequest feed) {

        if (TransitDataFormat.forField(feed.format()).isRealtime()) {
            return feedService.createFeed(feed)
                .map(Responses::ok)
                .orElse(Responses.notFound("Error creating the feed"));

        } else {
            return Responses.badRequest("Format not realtime");
        }

    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<List<Feed>> getFeeds() {
        return ResponseEntity.ok(
            feedService.getAllFeeds());
    }

}
