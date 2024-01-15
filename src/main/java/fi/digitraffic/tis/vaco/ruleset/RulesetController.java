package fi.digitraffic.tis.vaco.ruleset;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/rules")
@PreAuthorize("hasAuthority('vaco.apiuser')")
public class RulesetController {

    private final RulesetService rulesetService;
    private final MeService meService;

    public RulesetController(RulesetService rulesetService, MeService meService) {
        this.rulesetService = Objects.requireNonNull(rulesetService);
        this.meService = meService;
    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<List<Resource<Ruleset>>> listRulesets(@RequestParam(name = "businessId") String businessId) {
        if (meService.isAllowedToAccess(businessId)) {
            return ResponseEntity.ok(
                Streams.collect(rulesetService.selectRulesets(businessId), Resource::resource));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
