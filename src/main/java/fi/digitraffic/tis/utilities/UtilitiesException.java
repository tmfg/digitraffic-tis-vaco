package fi.digitraffic.tis.utilities;

import fi.digitraffic.tis.vaco.VacoException;

public class UtilitiesException extends VacoException {
    public UtilitiesException(String message) {
        super(message);
    }

    public UtilitiesException(String message, Throwable cause) {
        super(message, cause);
    }
}
