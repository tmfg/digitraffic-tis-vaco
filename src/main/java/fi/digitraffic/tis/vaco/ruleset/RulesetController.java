package fi.digitraffic.tis.vaco.ruleset;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rules")
public class RulesetController {

    private final RulesetService rulesetService;

    public RulesetController(RulesetService rulesetService) {
        this.rulesetService = rulesetService;
    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<List<Resource<Ruleset>>> listRulesets(
        @RequestParam String businessId
    ) {
        // TODO: Once we have authentication there needs to be an authentication check that the calling user has access
        //       to the businessId. No authentication yet though, so no such check either.
        return ResponseEntity.ok(
            Streams.map(rulesetService.selectRulesets(businessId), RulesetController::asRulesetResources)
                .toList());
    }


    private static Resource<Ruleset> asRulesetResources(Ruleset organization) {
        return new Resource<>(organization, null, Map.of());
    }

}
