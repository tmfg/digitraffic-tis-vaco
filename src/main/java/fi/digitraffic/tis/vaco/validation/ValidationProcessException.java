package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.vaco.VacoException;

/**
 * Common wrapping supertype for all non-recoverable exceptions thrown during validation process.
 */
public class ValidationProcessException extends VacoException {
    public ValidationProcessException(String message) {
        super(message);
    }

    public ValidationProcessException(String message, Exception cause) {
        super(message, cause);
    }
}
