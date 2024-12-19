package fi.digitraffic.exceptions;

import fi.digitraffic.DigitrafficRuntimeException;

/**
 * Common root type for all components which could be extracted to their own libraries.
 */
public abstract class LibraryException extends DigitrafficRuntimeException {

    protected LibraryException(String message) {
        super(message);
    }

    protected LibraryException(String message, Throwable cause) {
        super(message, cause);
    }

    protected LibraryException(Throwable cause) {
        super(cause);
    }
}
