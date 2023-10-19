package fi.digitraffic.tis.exceptions;

import fi.digitraffic.tis.TisException;

/**
 * Common root type for all components which could be extracted to their own libraries.
 */
public abstract class LibraryException extends TisException {
    protected LibraryException(String message) {
        super(message);
    }

    protected LibraryException(String message, Throwable cause) {
        super(message, cause);
    }

    protected LibraryException(Throwable cause) {
        super(cause);
    }

    protected LibraryException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
