package fi.digitraffic.tis.vaco;

import fi.digitraffic.exceptions.ServiceException;

/**
 * Common wrapping supertype for all custom expressions within VACO service itself.
 * <p>
 * You shouldn't instantiate this class directly, instead use subsystem specific child class.
 */
public abstract class VacoException extends ServiceException {

    public VacoException(String message) {
        super(message);
    }

    public VacoException(String message, Throwable cause) {
        super(message, cause);
    }

    public VacoException(Throwable cause) {
        super(cause);
    }
}
