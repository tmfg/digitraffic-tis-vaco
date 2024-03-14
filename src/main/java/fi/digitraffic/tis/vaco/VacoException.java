package fi.digitraffic.tis.vaco;

import fi.digitraffic.exceptions.ServiceException;

/**
 * Common wrapping supertype for all custom expressions within VACO service itself.
 * <p>
 * It's recommended you create a subclass if you have a specific need/use case that you may want to handle specifically
 * somewhere in the application, otherwise use this class directly.
 */
public class VacoException extends ServiceException {

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
