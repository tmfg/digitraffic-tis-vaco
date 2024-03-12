package fi.digitraffic.exceptions;

import fi.digitraffic.DigitrafficRuntimeException;

/**
 * Common root type for all components which could be extracted to their own libraries.
 */
public abstract class LibraryException extends DigitrafficRuntimeException {

    public LibraryException(String message) {
        super(message);
    }

    public LibraryException(String message, Throwable cause) {
        super(message, cause);
    }

    public LibraryException(Throwable cause) {
        super(cause);
    }
}
