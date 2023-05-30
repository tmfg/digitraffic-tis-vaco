package fi.digitraffic.tis.vaco;

/**
 * Common wrapping supertype for all custom expressions within VACO.
 *
 * You shouldn't instantiate this class directly, instead use subsystem specific child class.
 */
public abstract class VacoException extends RuntimeException {
    public VacoException(String message) {
        super(message);
    }

    public VacoException(String message, Throwable cause) {
        super(message, cause);
    }
}
