package fi.digitraffic.tis.vaco.organization;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.ItemExistsException;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/organization")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @RequestMapping(method = RequestMethod.POST, path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<ImmutableOrganization> createOrganization(@Valid @RequestBody ImmutableOrganization organization) {
        try {
            ImmutableOrganization created = organizationService.createOrganization(organization);
            return ResponseEntity.ok(created);
        } catch (ItemExistsException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @RequestMapping(method = RequestMethod.GET, path = "/{businessId}")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<ImmutableOrganization> getOrganizationByBusinessId(@PathVariable("businessId") String businessId) {
        try {
            ImmutableOrganization organization = organizationService.getByBusinessId(businessId);
            return ResponseEntity.ok(organization);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "An organization with given business ID does not exist");
        }
    }
}
