package fi.digitraffic.tis.vaco.company;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.vaco.api.model.Link;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.me.model.ImmutableMe;
import fi.digitraffic.tis.vaco.me.model.Me;
import io.swagger.v3.oas.annotations.Hidden;
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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.fromMethodCall;
import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@RestController
@RequestMapping({"/v1/companies", "/company"}) // /company is legacy name and should be refactored away from UI
@PreAuthorize("hasAuthority('vaco.apiuser') and hasAnyAuthority('vaco.company_admin')")
@Hidden
public class CompaniesController {

    private final MeService meService;

    private final VacoProperties vacoProperties;

    private final CompanyHierarchyService companyHierarchyService;

    public CompaniesController(VacoProperties vacoProperties,
                               CompanyHierarchyService companyHierarchyService,
                               MeService meService) {
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.companyHierarchyService = Objects.requireNonNull(companyHierarchyService);
        this.meService = Objects.requireNonNull(meService);
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<Company>> createCompany(@Valid @RequestBody Company company) {
        Optional<Company> createdCompany = companyHierarchyService.createCompany(company);
        return createdCompany
            .map(o -> ResponseEntity.ok(asCompanyResource(o)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                String.format("An company with business ID %s already exists", company.businessId())));
    }

    @PreAuthorize("hasAuthority('vaco.apiuser')")
    @GetMapping(path = "/{businessId}")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<Company>> getCompanyByBusinessId(@PathVariable("businessId") String businessId) {
        return companyHierarchyService.findByBusinessId(businessId)
            .map(o -> ResponseEntity.ok(asCompanyResource(o)))
            .orElse(Responses.notFound(String.format("A company with business ID %s does not exist", businessId)));
    }

    @PreAuthorize("hasAuthority('vaco.apiuser')")
    @GetMapping(path = "/members/{oid}")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<Me>> findUserCompanies(@PathVariable("oid") String oid) {
        Set<Company> companies = meService.findCompanies(oid);

        return ResponseEntity.ok(
            new Resource<>(
                ImmutableMe.of(companies),
                null,
                Map.of()));
    }

    private Resource<Company> asCompanyResource(Company company) {
        return new Resource<>(company, null, Map.of("refs", Map.of("self", Link.to(vacoProperties.baseUrl(),
            RequestMethod.GET,
            fromMethodCall(on(CompaniesController.class).getCompanyByBusinessId(company.businessId()))))));
    }

}
