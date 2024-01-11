package fi.digitraffic.tis.vaco.caching;

import fi.digitraffic.tis.vaco.VacoException;

/**
 * Should be thrown mainly when caching fails in disastrous, unexpected and probably in completely broken way.
 */
public class CachingFailureException extends VacoException {
    public CachingFailureException(String message) {
        super(message);
    }

    public CachingFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
