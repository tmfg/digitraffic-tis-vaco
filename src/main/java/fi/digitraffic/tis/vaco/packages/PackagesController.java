package fi.digitraffic.tis.vaco.packages;

import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.process.TaskService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@RestController
@RequestMapping("/packages")
@PreAuthorize("hasAuthority('vaco.apiuser')")
public class PackagesController {

    private final PackagesService packagesService;
    private final EntryService entryService;
    private final TaskService taskService;

    public PackagesController(PackagesService packagesService,
                              EntryService entryService,
                              TaskService taskService) {
        this.packagesService = Objects.requireNonNull(packagesService);
        this.entryService = Objects.requireNonNull(entryService);
        this.taskService = Objects.requireNonNull(taskService);
    }

    @GetMapping(path = "/{entryId}/{taskName}/{packageName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public FileSystemResource fetchPackage(
        @PathVariable("entryId") String entryPublicId,
        @PathVariable("taskName") String taskName,
        @PathVariable("packageName") String packageName,
        HttpServletResponse response) {

        return entryService.findEntry(entryPublicId)
            .flatMap(e ->
                taskService.findTask(entryPublicId, taskName)
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
