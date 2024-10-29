package fi.digitraffic.tis.vaco.company;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.company.dto.ImmutablePartnershipRequest;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.company.model.HierarchyType;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RestController
@RequestMapping("/partnership")
@Validated
@PreAuthorize("hasAuthority('vaco.apiuser') and hasAuthority('vaco.company_admin')")
@Hidden
public class PartnershipController {

    private final CompanyHierarchyService companyHierarchyService;

    public PartnershipController(CompanyHierarchyService companyHierarchyService) {
        this.companyHierarchyService = companyHierarchyService;
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<Partnership>> createPartnership(@Valid @RequestBody ImmutablePartnershipRequest partnershipRequest) {
        Optional<Company> partnerA = companyHierarchyService.findByBusinessId(partnershipRequest.partnerABusinessId());
        Optional<Company> partnerB = companyHierarchyService.findByBusinessId(partnershipRequest.partnerBBusinessId());

        if (partnerA.isEmpty() || partnerB.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Either of the provided company' business ID (%s, %s) does not exist",
                    partnershipRequest.partnerABusinessId(), partnershipRequest.partnerBBusinessId()));
        }

        Optional<Partnership> partnership = companyHierarchyService.createPartnership(HierarchyType.AUTHORITY_PROVIDER, partnerA.get(), partnerB.get());

        return partnership
            .map(value -> ResponseEntity.ok(Resource.resource(value)))
            .orElse(Responses.conflict(String.format("A partnership between provided business ID (%s, %s) already exists",
                partnershipRequest.partnerABusinessId(), partnershipRequest.partnerBBusinessId())));
    }
}
