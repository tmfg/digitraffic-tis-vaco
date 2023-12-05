package fi.digitraffic.tis.vaco.packages;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.queuehandler.QueueHandlerService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@RestController
@RequestMapping("/packages")
public class PackagesController {

    private final PackagesService packagesService;
    private final QueueHandlerService queueHandlerService;

    public PackagesController(PackagesService packagesService,
                              QueueHandlerService queueHandlerService) {
        this.packagesService = Objects.requireNonNull(packagesService);
        this.queueHandlerService = queueHandlerService;
    }

    @GetMapping(path = "/{entryId}/{taskName}/{packageName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public FileSystemResource fetchPackage(
        @PathVariable("entryId") String entryPublicId,
        @PathVariable("taskName") String taskName,
        @PathVariable("packageName") String packageName,
        HttpServletResponse response) {
        return queueHandlerService.getEntry(entryPublicId, false)
            .flatMap(e -> Streams.filter(e.tasks(), t -> t.name().equals(taskName))
                    .findFirst()
                    .flatMap(t -> packagesService.downloadPackage(e, t, packageName)))
            .map(filePath -> {
                ContentDisposition contentDisposition = ContentDisposition.builder("inline")
                    .filename(packageName + ".zip")
                    .build();
                response.addHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());

                return new FileSystemResource(filePath);
            })
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                String.format("Unknown package '%s' for entry '%s'", packageName, entryPublicId)));
    }
}
