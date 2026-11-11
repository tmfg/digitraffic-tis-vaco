package fi.digitraffic.tis.vaco.ruleset;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.company.model.Company;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * Admin-only endpoints for managing explicit ruleset access grants.
 */
@RestController
@RequestMapping("/admin/rulesets")
@Hidden
public class RulesetAccessController {

    private final RulesetAccessService rulesetAccessService;

    public RulesetAccessController(RulesetAccessService rulesetAccessService) {
        this.rulesetAccessService = Objects.requireNonNull(rulesetAccessService);
    }

    @PostMapping("/{rulesetName}/grants")
    @PreAuthorize("hasAuthority('vaco.admin')")
    @JsonView(DataVisibility.AdminRestricted.class)
    public ResponseEntity<Resource<Boolean>> createGrant(
            @PathVariable String rulesetName,
            @Valid @RequestBody GrantRequest request) {
        boolean created = rulesetAccessService.grantAccess(request.businessId(), rulesetName);
        if (created) {
            return Responses.ok(true);
        } else {
            return Responses.badRequest("Grant already exists or company/ruleset not found");
        }
    }

    @DeleteMapping("/{rulesetName}/grants/{businessId}")
    @PreAuthorize("hasAuthority('vaco.admin')")
    @JsonView(DataVisibility.AdminRestricted.class)
    public ResponseEntity<Resource<Boolean>> deleteGrant(
            @PathVariable String rulesetName,
            @PathVariable String businessId) {
        rulesetAccessService.revokeAccess(businessId, rulesetName);
        return Responses.ok(true);
    }

    @GetMapping("/{rulesetName}/grants")
    @PreAuthorize("hasAuthority('vaco.admin')")
    @JsonView(DataVisibility.AdminRestricted.class)
    public ResponseEntity<Resource<List<Company>>> listGrants(@PathVariable String rulesetName) {
        return rulesetAccessService.listGrants(rulesetName)
            .map(Responses::ok)
            .orElseGet(() -> Responses.badRequest("No such ruleset: " + rulesetName));
    }

    public record GrantRequest(@NotBlank String businessId) {}
}
