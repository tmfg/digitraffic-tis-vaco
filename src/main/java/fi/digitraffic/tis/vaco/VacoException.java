package fi.digitraffic.tis.vaco;

import fi.digitraffic.tis.TisException;

/**
 * Common wrapping supertype for all custom expressions within VACO.
 *
 * You shouldn't instantiate this class directly, instead use subsystem specific child class.
 */
public abstract class VacoException extends TisException {
    public VacoException(String message) {
        super(message);
    }

    public VacoException(String message, Throwable cause) {
        super(message, cause);
    }
}
