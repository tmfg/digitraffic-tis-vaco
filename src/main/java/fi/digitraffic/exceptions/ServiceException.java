package fi.digitraffic.exceptions;

import fi.digitraffic.DigitrafficRuntimeException;

public class ServiceException extends DigitrafficRuntimeException {
    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceException(Throwable cause) {
        super(cause);
    }
}
