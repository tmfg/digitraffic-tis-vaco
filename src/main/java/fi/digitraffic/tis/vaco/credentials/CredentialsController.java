package fi.digitraffic.tis.vaco.credentials;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.api.model.credentials.CreateCredentialsRequest;
import fi.digitraffic.tis.vaco.api.model.credentials.UpdateCredentialsRequest;
import fi.digitraffic.tis.vaco.credentials.model.Credentials;
import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping({"/v1/credentials", "/credentials"})
public class CredentialsController {

    private final CredentialsService credentialsService;

    public CredentialsController(CredentialsService credentialsService) {
        this.credentialsService = Objects.requireNonNull(credentialsService);
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<Resource<Credentials>> create(@Valid @RequestBody CreateCredentialsRequest credentials) {
        return switch (credentials.type().fieldName()) {
            case CredentialsType.Name.HTTP_BASIC -> credentialsService.createCredentials(credentials)
                .map(Responses::created)
                .orElseGet(() -> Responses.badRequest(
                    String.format(
                        "Failed to store credentials '%s'/'%s' for %s",
                        credentials.name(),
                        credentials.description(),
                        credentials.owner()
                    )));
            default -> Responses.badRequest(String.format("Unsupported credentials type '%s'", credentials.type()));
        };
    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<Resource<List<Credentials>>> fetchAll(@RequestParam("businessId") String businessId) {
        return credentialsService.findAllForBusinessId(businessId)
            .map(Responses::ok)
            .orElseGet(() -> Responses.badRequest(String.format("Could not fetch all credentials for '%s'", businessId)));
    }

    @GetMapping(path = "/{publicId}")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<Resource<Credentials>> fetchCredentials(@PathVariable("publicId") String publicId) {
        return credentialsService.findByPublicId(publicId)
            .map(Responses::ok)
            .orElseGet(() -> Responses.badRequest(String.format("Could not fetch credentials with id '%s'", publicId)));
    }

    @PutMapping(path = "/{publicId}")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<Resource<Credentials>> updateCredentials(@PathVariable("publicId") String publicId,
                                                                   @Valid @RequestBody UpdateCredentialsRequest credentials) {
        // TODO: this definitely needs access check
        return credentialsService.updateCredentials(publicId, credentials)
            .map(Responses::ok)
            .orElseGet(() -> Responses.badRequest(String.format("Could not update credentials with id '%s'", publicId)));
    }

    @DeleteMapping(path = "/{publicId}")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<Resource<Boolean>> deleteCredentials(@PathVariable("publicId") String publicId) {
        return credentialsService.deleteCredentials(publicId)
            .map(Responses::ok)
            .orElseGet(() -> Responses.badRequest(String.format("Could not delete credentials with id '%s'", publicId)));
    }

}
