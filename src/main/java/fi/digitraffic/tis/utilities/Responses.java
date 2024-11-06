package fi.digitraffic.tis.utilities;

import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.feeds.model.Feed;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
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

    public static <D> ResponseEntity<Resource<D>> conflict(String error) {
        return new ResponseEntity<>(new Resource<>(null, error, null), HttpStatus.CONFLICT);
    }

    public static <D> ResponseEntity<Resource<D>> unauthorized(String error) {
        return new ResponseEntity<>(new Resource<>(null, error, null), HttpStatus.UNAUTHORIZED);
    }

    public static <D> ResponseEntity<Resource<D>> badRequest(String error) {
        return new ResponseEntity<>(new Resource<>(null, error, null), HttpStatus.BAD_REQUEST);
    }

    public static <D> ResponseEntity<Resource<D>> created(D data) {
        return new ResponseEntity<>(new Resource<>(data, null, null), HttpStatus.CREATED);
    }

    public static <D> ResponseEntity<Resource<D>> ok(D data) {
        return ResponseEntity.ok(Resource.resource(data));
    }
}
