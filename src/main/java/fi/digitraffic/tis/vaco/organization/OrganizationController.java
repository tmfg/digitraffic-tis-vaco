package fi.digitraffic.tis.vaco.organization;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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

    @RequestMapping(method = RequestMethod.POST, path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<ImmutableOrganization>> createOrganization(@Valid @RequestBody ImmutableOrganization organization) {
        Optional<ImmutableOrganization> createdOrganization = organizationService.createOrganization(organization);
        return createdOrganization
            .map(o -> ResponseEntity.ok(asOrganizationResource(o)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "An organization with given business ID already exists"));
    }

    @RequestMapping(method = RequestMethod.GET, path = "/{businessId}")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<ImmutableOrganization>> getOrganizationByBusinessId(@PathVariable("businessId") String businessId) {
        Optional<ImmutableOrganization> organization = organizationService.findByBusinessId(businessId);
        return organization
            .map(o -> ResponseEntity.ok(asOrganizationResource(o)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "An organization with given business ID does not exist"));
    }

    private static Resource<ImmutableOrganization> asOrganizationResource(ImmutableOrganization organization) {
        return new Resource<>(organization, Map.of("self", linkToGetOrganization(organization)));
    }

    private static Link linkToGetOrganization(ImmutableOrganization organization) {
        return new Link(
            MvcUriComponentsBuilder
                .fromMethodCall(on(OrganizationController.class).getOrganizationByBusinessId(organization.businessId()))
                .toUriString(),
            RequestMethod.GET);
    }
}
