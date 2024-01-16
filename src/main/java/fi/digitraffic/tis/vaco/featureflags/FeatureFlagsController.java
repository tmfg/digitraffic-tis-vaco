package fi.digitraffic.tis.vaco.featureflags;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.featureflags.model.FeatureFlag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

import static fi.digitraffic.tis.utilities.JwtHelpers.safeGet;
import static fi.digitraffic.tis.utilities.dto.Resource.resource;
import static org.springframework.http.ResponseEntity.badRequest;
import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping("/feature-flags")
@PreAuthorize("hasAuthority('vaco.apiuser') and hasAuthority('vaco.company_admin')")
public class FeatureFlagsController {

    private final FeatureFlagsService featureFlagsService;

    public FeatureFlagsController(FeatureFlagsService featureFlagsService) {
        this.featureFlagsService = Objects.requireNonNull(featureFlagsService);
    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<List<FeatureFlag>>> listKnownFeatureFlags() {
        return ok(resource(featureFlagsService.listFeatureFlags()));
    }

    @PostMapping(path = "/{name}/{action}")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<FeatureFlag>> modifyFeatureFlag(@PathVariable("name") String name,
                                                                   @PathVariable("action") String action,
                                                                   JwtAuthenticationToken token) {
        return featureFlagsService.findFeatureFlag(name).map(flag -> switch (action) {
                case "enable" -> ok(resource(featureFlagsService.setFeatureFlag(flag, true, safeGet(token, "oid").orElse(null))));
                case "disable" -> ok(resource(featureFlagsService.setFeatureFlag(flag, false, safeGet(token, "oid").orElse(null))));
                default -> badRequest().body(resource(flag, "Unknown action '" + action + "'"));
            })
            .orElseGet(() -> badRequest().body(resource(null, "Unknown feature flag '" + name + "'")));
    }
}
