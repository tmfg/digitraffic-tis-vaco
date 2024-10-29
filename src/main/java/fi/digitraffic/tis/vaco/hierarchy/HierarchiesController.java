package fi.digitraffic.tis.vaco.hierarchy;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.Responses;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.api.model.hierarchies.CreateHierarchyRequest;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.hierarchy.model.Hierarchy;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static fi.digitraffic.tis.vaco.api.model.Resource.resource;
import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping("/v1/hierarchies")
public class HierarchiesController {

    private final CompanyHierarchyService companyHierarchyService;
    private final HierarchiesService hierarchiesService;

    public HierarchiesController(HierarchiesService hierarchiesService, CompanyHierarchyService companyHierarchyService) {
        this.hierarchiesService = Objects.requireNonNull(hierarchiesService);
        this.companyHierarchyService = companyHierarchyService;
    }

    @GetMapping("")
    public ResponseEntity<Resource<List<Hierarchy>>> getAllHierarchies() {
        return ok(resource(hierarchiesService.listAll()));
    }

    @PostMapping(path = "")
    @JsonView(DataVisibility.Public.class)
    public ResponseEntity<Resource<Hierarchy>> createHierarchy(@Valid @RequestBody CreateHierarchyRequest createHierarchyRequest) {
        Optional<Company> company = companyHierarchyService.findByBusinessId(createHierarchyRequest.company().businessId());
        if (company.isEmpty()) {
            return Responses.badRequest(String.format("Unknown company '%s'", createHierarchyRequest.company().businessId()));
        }
        Optional<Hierarchy> createdHierarchy = hierarchiesService.createHierarchy(company.get(), createHierarchyRequest.type());
        return createdHierarchy
            .map(Responses::created)
            .orElseGet(() -> Responses.conflict(String.format("Hierarchy of type %s for company %s already exists",
                createHierarchyRequest.type(),
                createHierarchyRequest.company().businessId())));
    }
}
