package fi.digitraffic.tis.exceptions;

import fi.digitraffic.tis.vaco.VacoException;

public class PersistenceException extends VacoException {
    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PersistenceException(Throwable cause) {
        super(cause);
    }
}
