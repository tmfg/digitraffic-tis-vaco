package fi.digitraffic.tis.vaco.company;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.vaco.company.dto.ImmutableCooperationRequest;
import fi.digitraffic.tis.vaco.company.model.Cooperation;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CooperationService;
import fi.digitraffic.tis.vaco.company.service.CompanyService;
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

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/cooperation")
@Validated
public class CooperationController {

    private final CooperationService cooperationService;
    private final CompanyService companyService;

    public CooperationController(CooperationService cooperationService, CompanyService companyService) {
        this.cooperationService = cooperationService;
        this.companyService = companyService;
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAuthority('APPROLE_vaco.admin')")
    public ResponseEntity<Resource<Cooperation>> createCooperation(@Valid @RequestBody ImmutableCooperationRequest cooperationRequest) {
        Optional<Company> partnerA = companyService.findByBusinessId(cooperationRequest.partnerABusinessId());
        Optional<Company> partnerB = companyService.findByBusinessId(cooperationRequest.partnerBBusinessId());

        if (partnerA.isEmpty() || partnerB.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Either of the provided company' business ID (%s, %s) does not exist",
                    cooperationRequest.partnerABusinessId(), cooperationRequest.partnerBBusinessId()));
        }

        Optional<Cooperation> cooperation = cooperationService.create(cooperationRequest.cooperationType(), partnerA.get(), partnerB.get());

        return cooperation
            .map(value -> ResponseEntity.ok(new Resource<>(value, null, Map.of())))
            .orElse(Responses.conflict(String.format("A cooperation between provided business ID (%s, %s) already exists",
                cooperationRequest.partnerABusinessId(), cooperationRequest.partnerBBusinessId())));
    }
}
