package fi.digitraffic.tis.vaco.badges;

import fi.digitraffic.tis.vaco.entries.EntryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@RestController
@RequestMapping("/badge")
@PreAuthorize("hasAuthority('vaco.user') or hasAuthority('vaco.apiuser')")
public class BadgeController {

    private final EntryService entryService;

    public BadgeController(EntryService entryService) {
        this.entryService = Objects.requireNonNull(entryService);
    }

    @GetMapping(path = "/{publicId}", produces = "image/svg+xml")
    public ClassPathResource entryBadge(@PathVariable("publicId") String publicId,
                                        HttpServletResponse response) {

        return entryService.getStatus(publicId)
            .map(status -> {
                ContentDisposition contentDisposition = ContentDisposition.builder("inline")
                    .filename(status.fieldName() + ".svg")
                    .build();
                response.addHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());

                return new ClassPathResource("badges/" + status.fieldName() + ".svg");
            })
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                String.format("No status available for entry '%s'", publicId)));
    }

    @GetMapping(path = "/{publicId}/{taskName}", produces = "image/svg+xml")
    public ClassPathResource taskBadge(@PathVariable("publicId") String publicId,
                                       @PathVariable("taskName") String taskName,
                                       HttpServletResponse response) {

        return entryService.getStatus(publicId, taskName)
            .map(status -> {
                ContentDisposition contentDisposition = ContentDisposition.builder("inline")
                    .filename(status.fieldName() + ".svg")
                    .build();
                response.addHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());

                return new ClassPathResource("badges/" + status.fieldName() + ".svg");
            })
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                String.format("No status available for entry '%s' task '%s'", publicId, taskName)));
    }
}
