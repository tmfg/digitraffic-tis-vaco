package fi.digitraffic.tis.vaco.organization;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.organization.dto.ImmutableCooperationRequest;
import fi.digitraffic.tis.vaco.organization.model.Cooperation;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.service.CooperationService;
import fi.digitraffic.tis.vaco.organization.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/cooperation")
@Validated
public class CooperationController {

    private final CooperationService cooperationService;
    private final OrganizationService organizationService;

    public CooperationController(CooperationService cooperationService, OrganizationService organizationService) {
        this.cooperationService = cooperationService;
        this.organizationService = organizationService;
    }

    @RequestMapping(method = RequestMethod.POST, path = "")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<ImmutableCooperationRequest>> createCooperation(@Valid @RequestBody ImmutableCooperationRequest cooperationRequest) {
        Optional<ImmutableOrganization> partnerA = organizationService.findByBusinessId(cooperationRequest.partnerABusinessId());
        Optional<ImmutableOrganization> partnerB = organizationService.findByBusinessId(cooperationRequest.partnerBBusinessId());

        if (partnerA.isEmpty() || partnerB.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Either of the provided organizations' business ID does not exist");
        }

        Optional<Cooperation> cooperation = cooperationService.create(cooperationRequest.cooperationType(), partnerA.get(), partnerB.get());

        if (cooperation.isPresent()) {
            return ResponseEntity.ok(new Resource<>(cooperationRequest, Map.of()));
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A cooperation between given business ID-s already exists");
        }
    }
}
