package fi.digitraffic.tis.vaco.me;

import fi.digitraffic.tis.vaco.VacoException;

public class UnauthenticatedException extends VacoException {
    public UnauthenticatedException(String message) {
        super(message);
    }

    public UnauthenticatedException(String message, Throwable cause) {
        super(message, cause);
    }
}