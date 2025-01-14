package fi.digitraffic.tis.vaco.badges;

import com.google.common.annotations.VisibleForTesting;
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
@RequestMapping({"/v1/badge", "/badge"})
public class BadgeController {

    private final BadgeService badgeService;

    public BadgeController(BadgeService badgeService) {
        this.badgeService = Objects.requireNonNull(badgeService);
    }

    @GetMapping(path = "/{publicId}", produces = "image/svg+xml")
    public ClassPathResource entryBadge(@PathVariable("publicId") String publicId,
                                        HttpServletResponse response) {
        return getResource(response, badgeService.getBadge(publicId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                String.format("No status available for entry '%s'", publicId)));
    }

    @GetMapping(path = "/{publicId}/{taskName}", produces = "image/svg+xml")
    public ClassPathResource taskBadge(@PathVariable("publicId") String publicId,
                                       @PathVariable("taskName") String taskName,
                                       HttpServletResponse response) {
        return getResource(response, badgeService.getBadge(publicId, taskName))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                String.format("No status available for entry '%s' task '%s'", publicId, taskName)));
    }

    @VisibleForTesting
    static Optional<ClassPathResource> getResource(HttpServletResponse response, Optional<ClassPathResource> statusOpt) {
        return statusOpt.map(status -> {
                ContentDisposition contentDisposition = ContentDisposition.builder("inline")
                    .filename(status.getFilename())
                    .build();
                response.addHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
                return status;
            });
    }
}
