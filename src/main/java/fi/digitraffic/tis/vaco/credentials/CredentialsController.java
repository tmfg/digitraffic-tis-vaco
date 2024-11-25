package fi.digitraffic.tis.vaco.credentials;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.api.model.credentials.CreateCredentialsRequest;
import fi.digitraffic.tis.vaco.api.model.credentials.UpdateCredentialsRequest;
import fi.digitraffic.tis.vaco.credentials.model.Credentials;
import fi.digitraffic.tis.vaco.credentials.model.CredentialsType;
import fi.digitraffic.tis.vaco.me.MeService;
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
    private final MeService meService;

    public CredentialsController(CredentialsService credentialsService, MeService meService) {
        this.credentialsService = Objects.requireNonNull(credentialsService);
        this.meService = Objects.requireNonNull(meService);
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<Resource<Credentials>> create(@Valid @RequestBody CreateCredentialsRequest credentials) {
        if (meService.isAllowedToAccess(credentials.owner())) {
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
        } else {
            return Responses.unauthorized("Not allowed to access %s".formatted(credentials.owner()));
        }
    }

    @GetMapping(path = "")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<Resource<List<Credentials>>> fetchAll(@RequestParam("businessId") String businessId) {
        if (meService.isAllowedToAccess(businessId)) {
            return credentialsService.findAllForBusinessId(businessId)
                .map(Responses::ok)
                .orElseGet(() -> Responses.badRequest(String.format("Could not fetch all credentials for '%s'", businessId)));
        } else {
            return Responses.unauthorized("Not allowed to access %s".formatted(businessId));
        }
    }

    @GetMapping(path = "/{publicId}")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<Resource<Credentials>> fetchCredentials(@PathVariable("publicId") String publicId) {
        return credentialsService.findByPublicId(publicId)
            .map(c -> {
                if (meService.isAllowedToAccess(c.owner())) {
                    return credentialsService.findByPublicId(publicId)
                        .map(Responses::ok)
                        .orElseGet(() -> Responses.badRequest(String.format("Could not fetch credentials with id '%s'", publicId)));
                } else {
                    return Responses.<Credentials>unauthorized("Not allowed to access %s".formatted(publicId));
                }
            })
            .orElseGet(() -> Responses.notFound("%s not found".formatted(publicId)));
    }

    @PutMapping(path = "/{publicId}")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<Resource<Credentials>> updateCredentials(@PathVariable("publicId") String publicId,
                                                                   @Valid @RequestBody UpdateCredentialsRequest credentials) {
        return credentialsService.findByPublicId(publicId)
            .map(c -> {
                if (meService.isAllowedToAccess(c.owner())) {
                    return credentialsService.updateCredentials(publicId, credentials)
                        .map(Responses::ok)
                        .orElseGet(() -> Responses.badRequest(String.format("Could not update credentials with id '%s'", publicId)));
                } else {
                    return Responses.<Credentials>unauthorized("Not allowed to access %s".formatted(publicId));
                }
            })
            .orElseGet(() -> Responses.notFound("%s not found".formatted(publicId)));
    }

    @DeleteMapping(path = "/{publicId}")
    @JsonView(DataVisibility.AdminRestricted.class)
    @PreAuthorize("hasAuthority('vaco.admin')")
    public ResponseEntity<Resource<Boolean>> deleteCredentials(@PathVariable("publicId") String publicId) {
        return credentialsService.findByPublicId(publicId)
            .map(c -> {
                if (meService.isAllowedToAccess(c.owner())) {
                    return credentialsService.deleteCredentials(publicId)
                        .map(Responses::ok)
                        .orElseGet(() -> Responses.badRequest(String.format("Could not delete credentials with id '%s'", publicId)));
                } else {
                    return Responses.<Boolean>unauthorized("Not allowed to access %s".formatted(publicId));
                }
            })
            .orElseGet(() -> Responses.notFound("%s not found".formatted(publicId)));
    }

}
