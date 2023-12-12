package fi.digitraffic.tis.vaco.badges;

import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.entries.model.Status;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/badge")
public class BadgeController {

    private final EntryService entryService;

    public BadgeController(EntryService entryService) {
        this.entryService = Objects.requireNonNull(entryService);
    }

    @GetMapping(path = "/{publicId}", produces = "image/svg+xml")
    public ClassPathResource entryBadge(@PathVariable("publicId") String publicId,
                                        HttpServletResponse response) {
        return getResource(response, entryService.getStatus(publicId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                String.format("No status available for entry '%s'", publicId)));
    }

    @GetMapping(path = "/{publicId}/{taskName}", produces = "image/svg+xml")
    public ClassPathResource taskBadge(@PathVariable("publicId") String publicId,
                                       @PathVariable("taskName") String taskName,
                                       HttpServletResponse response) {
        return getResource(response, entryService.getStatus(publicId, taskName))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                String.format("No status available for entry '%s' task '%s'", publicId, taskName)));
    }

    private static Optional<ClassPathResource> getResource(HttpServletResponse response, Optional<Status> statusOpt) {
        return statusOpt.map(status -> {
                ContentDisposition contentDisposition = ContentDisposition.builder("inline")
                    .filename(status.fieldName() + ".svg")
                    .build();
                response.addHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());

                return new ClassPathResource("badges/" + status.fieldName() + ".svg");
            });
    }
}
