package fi.digitraffic.exceptions;

import fi.digitraffic.DigitrafficRuntimeException;

public class ServiceException extends DigitrafficRuntimeException {
    protected ServiceException(String message) {
        super(message);
    }

    protected ServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    protected ServiceException(Throwable cause) {
        super(cause);
    }
}
