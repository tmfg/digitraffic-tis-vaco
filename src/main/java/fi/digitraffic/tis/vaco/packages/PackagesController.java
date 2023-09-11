package fi.digitraffic.tis.vaco.packages;

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

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/packages")
public class PackagesController {

    private final PackagesService packagesService;

    public PackagesController(PackagesService packagesService) {
        this.packagesService = Objects.requireNonNull(packagesService);
    }

    @GetMapping(path = "/{entryId}/{packageName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public FileSystemResource fetchPackage(
        @PathVariable("entryId") String entryPublicId,
        @PathVariable("packageName") String packageName,
        HttpServletResponse response) {
        Optional<Path> entry = packagesService.findPackage(entryPublicId, packageName);

        return entry
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
