package fi.digitraffic.tis.vaco.caching;

import com.fasterxml.jackson.annotation.JsonView;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.DataVisibility;
import fi.digitraffic.tis.vaco.caching.model.CacheSummaryStatistics;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

import static fi.digitraffic.tis.utilities.dto.Resource.resource;
import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping("/admin/caching")
@PreAuthorize("hasAuthority('APPROLE_vaco.admin')")
public class CachingController {

    private final CachingService cachingService;

    public CachingController(CachingService cachingService) {
        this.cachingService = Objects.requireNonNull(cachingService);
    }

    @GetMapping(path = "/statistics")
    @JsonView(DataVisibility.External.class)
    public ResponseEntity<Resource<Map<String, CacheSummaryStatistics>>> listGroupIdMappingTasks() {
        return ok(resource(cachingService.getStats()));
    }
}