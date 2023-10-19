package fi.digitraffic.tis.exceptions;

import fi.digitraffic.tis.TisException;

public class UtilitiesException extends TisException {
    public UtilitiesException(String message) {
        super(message);
    }

    public UtilitiesException(String message, Throwable cause) {
        super(message, cause);
    }
}
