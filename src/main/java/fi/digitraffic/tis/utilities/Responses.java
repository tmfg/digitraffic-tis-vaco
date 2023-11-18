package fi.digitraffic.tis.utilities;

import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.company.model.Cooperation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Convenience methods for building Spring Boot HTTP responses.
 */
public final class Responses {
    private Responses() {}

    public static <D> ResponseEntity<Resource<D>> notFound(String error) {
        return new ResponseEntity<>(new Resource<>(null, error, null), HttpStatus.NOT_FOUND);
    }

    public static ResponseEntity<Resource<Cooperation>> conflict(String error) {
        return new ResponseEntity<>(new Resource<>(null, error, null), HttpStatus.CONFLICT);
    }
}
