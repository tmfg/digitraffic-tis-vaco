package fi.digitraffic.tis.vaco.organization;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.model.Organization;
import fi.digitraffic.tis.vaco.organization.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@RestController
@RequestMapping("/organization")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAuthority('APPROLE_vaco.admin')")
    public ResponseEntity<Resource<Organization>> createOrganization(@Valid @RequestBody ImmutableOrganization organization) {
        Optional<Organization> createdOrganization = organizationService.createOrganization(organization);
        return createdOrganization
            .map(o -> ResponseEntity.ok(asOrganizationResource(o)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                String.format("An organization with business ID %s already exists", organization.businessId())));
    }

    @GetMapping(path = "/{businessId}")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<Organization>> getOrganizationByBusinessId(@PathVariable("businessId") String businessId) {
        Optional<Organization> organization = organizationService.findByBusinessId(businessId);
        return organization
            .map(o -> ResponseEntity.ok(asOrganizationResource(o)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                String.format("An organization with business ID %s does not exist", businessId)));
    }

    private static Resource<Organization> asOrganizationResource(Organization organization) {
        return new Resource<>(organization, Map.of("self", linkToGetOrganization(organization)));
    }

    private static Link linkToGetOrganization(Organization organization) {
        return new Link(
            MvcUriComponentsBuilder
                .fromMethodCall(on(OrganizationController.class).getOrganizationByBusinessId(organization.businessId()))
                .toUriString(),
            RequestMethod.GET);
    }
}
