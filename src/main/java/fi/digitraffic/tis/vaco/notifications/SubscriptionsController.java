package fi.digitraffic.tis.vaco.notifications;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.api.model.notifications.CreateSubscriptionRequest;
import fi.digitraffic.tis.vaco.notifications.model.Subscription;
import fi.digitraffic.tis.vaco.notifications.model.SubscriptionType;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping("/v1/subscriptions")
public class SubscriptionsController {

    private final NotificationsService notificationsService;

    public SubscriptionsController(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    @PostMapping("")
    @JsonView(DataVisibility.AdminRestricted.class)
    public ResponseEntity<Resource<Subscription>> createSubscription(
        @Valid @RequestBody CreateSubscriptionRequest createSubscriptionRequest) {
        return switch (createSubscriptionRequest.type().fieldName()) {
            case "webhook" -> notificationsService.subscribe(
                    SubscriptionType.WEBHOOK,
                    createSubscriptionRequest.subscriber(),
                    createSubscriptionRequest.resource())
                .map(Responses::created)
                .orElseGet(() -> Responses.badRequest(
                    String.format(
                        "%s failed to subscribe to %s",
                        createSubscriptionRequest.subscriber(),
                        createSubscriptionRequest.resource()
                    )));
            default -> Responses.badRequest(String.format("Unsupported subscription type '%s'", createSubscriptionRequest.type()));
        };
    }

    @GetMapping("")
    @JsonView(DataVisibility.AdminRestricted.class)
    public ResponseEntity<Resource<List<Subscription>>> listSubscriptions(
        @RequestParam("subscriber") String businessId
    ) {
        return ok(Resource.resource(notificationsService.listSubscriptions(businessId)));
    }

    @DeleteMapping("/{publicId}")
    @JsonView(DataVisibility.AdminRestricted.class)
    public ResponseEntity<Resource<Boolean>> deleteSubscription(
        @PathVariable("publicId") String publicId
    ) {
        if (notificationsService.deleteSubscription(publicId)) {
            return noContent().build();
        } else {
            return Responses.badRequest(String.format("Subscription '%s' could not be deleted, check logs", publicId));
        }
    }

}
