package fi.digitraffic.tis.vaco.company;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CompanyService;
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
@RequestMapping("/company")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.External.class)
    @PreAuthorize("hasAuthority('APPROLE_vaco.admin')")
    public ResponseEntity<Resource<Company>> createCompany(@Valid @RequestBody Company company) {
        Optional<Company> createdCompany = companyService.createCompany(company);
        return createdCompany
            .map(o -> ResponseEntity.ok(asCompanyResource(o)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                String.format("An company with business ID %s already exists", company.businessId())));
    }

    @GetMapping(path = "/{businessId}")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<Company>> getCompanyByBusinessId(@PathVariable("businessId") String businessId) {
        return companyService.findByBusinessId(businessId)
            .map(o -> ResponseEntity.ok(asCompanyResource(o)))
            .orElse(Responses.notFound(String.format("A company with business ID %s does not exist", businessId)));
    }

    private static Resource<Company> asCompanyResource(Company company) {
        return new Resource<>(company, null, Map.of("refs", Map.of("self", linkToGetCompany(company))));
    }

    private static Link linkToGetCompany(Company company) {
        return new Link(
                MvcUriComponentsBuilder
                        .fromMethodCall(on(CompanyController.class).getCompanyByBusinessId(company.businessId()))
                        .toUriString(),
                RequestMethod.GET);
    }
}