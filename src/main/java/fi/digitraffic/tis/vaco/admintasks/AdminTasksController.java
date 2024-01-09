package fi.digitraffic.tis.vaco.admintasks;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.admintasks.model.GroupIdMappingTask;
import fi.digitraffic.tis.vaco.company.service.CompanyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static fi.digitraffic.tis.utilities.dto.Resource.resource;
import static org.springframework.http.ResponseEntity.badRequest;
import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping("/admin-tasks")
@PreAuthorize("hasAuthority('APPROLE_vaco.admin')")
public class AdminTasksController {

    private final AdminTasksService adminTasksService;
    private final CompanyService companyService;

    public AdminTasksController(AdminTasksService adminTasksService, CompanyService companyService) {
        this.adminTasksService = Objects.requireNonNull(adminTasksService);
        this.companyService = Objects.requireNonNull(companyService);
    }

    @GetMapping(path = "/group-ids")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<List<GroupIdMappingTask>>> listGroupIdMappingTasks() {
        return ok(resource(adminTasksService.listUnresolvedGroupIdMappingTasks()));
    }

    @GetMapping(path = "/group-ids/{publicId}/{action}")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<GroupIdMappingTask>> listGroupIdMappingTasks(
        @PathVariable("publicId") String publicId,
        @PathVariable("action") String action,
        @RequestParam(value = "businessId", required = false) String businessId) {
        Optional<GroupIdMappingTask> task = adminTasksService.findGroupIdMappingTaskByPublicId(publicId);
        return task.map(t -> switch (action) {
            case "skip" -> {
                GroupIdMappingTask data = adminTasksService.resolveAsSkippable(t);
                yield ok(resource(data));
            }
            case "assign" -> companyService.findByBusinessId(businessId)
                    .map(c -> {
                        GroupIdMappingTask data = adminTasksService.resolveAsPairedToCompany(t, c).getLeft();
                        return ok(resource(data));
                    })
                    .orElseGet(() -> badRequest().body(resource(t, "Must provide businessId as query parameter when doing group id assignment; businessId is null")));
            default -> badRequest().body(resource(t, "Unknown action '" + action + "'"));
        }).orElseGet(() -> badRequest().body(resource(null, "Unknown task '" + publicId + "'")));
    }

}
