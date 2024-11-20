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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

import static org.springframework.http.ResponseEntity.noContent;

@RestController
@RequestMapping({"/v1/feeds", "/feeds"})
public class FeedController {

    private final FeedService feedService;
    public FeedController(FeedService feedService) {
        this.feedService = Objects.requireNonNull(feedService);
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
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
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<List<Feed>> listFeeds() {
        return ResponseEntity.ok(
            feedService.listAllFeeds());
    }

    @PostMapping(path = "/{publicId}")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<Resource<Feed>> modifyFeed(@PathVariable("publicId") String publicId,
                                                     @RequestParam("enableProcessing") boolean processingEnabled) {

        return feedService.modifyFeedProcessing(processingEnabled, publicId)
            .map(Responses::ok)
            .orElse(Responses.badRequest("error modifying processing enabling"));

    }

    @DeleteMapping(path = "/{publicId}")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<Resource<Boolean>> deleteFeed(@PathVariable("publicId") String publicId) {

        if (feedService.deleteFeedByPublicId(publicId)) {
            return noContent().build();
        } else {
            return Responses.badRequest("Error when deleting feed");
        }
    }

}
