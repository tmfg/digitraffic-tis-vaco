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

    public static ResponseEntity<Resource<Partnership>> conflict(String error) {
        return new ResponseEntity<>(new Resource<>(null, error, null), HttpStatus.CONFLICT);
    }

    public static <D> ResponseEntity<Resource<D>> unauthorized(String error) {
        return new ResponseEntity<>(new Resource<>(null, error, null), HttpStatus.UNAUTHORIZED);
    }

    public static ResponseEntity<Resource<Entry>> badRequest(String error) {
        return new ResponseEntity<>(new Resource<>(null, error, null), HttpStatus.BAD_REQUEST);
    }

    public static ResponseEntity<String> badFeedRequest(String error) {
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
