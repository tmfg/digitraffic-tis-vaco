package fi.digitraffic.tis;

/**
 * Exception type hierarchy root for <i>all</i> exceptions thrown by TIS/VACO.
 * <p>
 * Do not use this type directly, instead use one of its applicable subtypes.
 */
public abstract class TisException extends RuntimeException {

    protected TisException(String message) {
        super(message);
    }

    protected TisException(String message, Throwable cause) {
        super(message, cause);
    }

    protected TisException(Throwable cause) {
        super(cause);
    }

    protected TisException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
