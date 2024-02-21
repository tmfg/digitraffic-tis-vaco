package fi.digitraffic;

/**
 * Common root exception type for all Digitraffic runtime exceptions. Exist mainly to provide support for
 * <pre>
 * try {
 *     ...
 * } catch (DigitrafficRuntimeException e) {
 *     logger.info("Something generic happened", e);
 * }
 * </pre>
 */
public abstract class DigitrafficRuntimeException extends RuntimeException {
    public DigitrafficRuntimeException(String message) {
        super(message);
    }

    public DigitrafficRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public DigitrafficRuntimeException(Throwable cause) {
        super(cause);
    }
}
