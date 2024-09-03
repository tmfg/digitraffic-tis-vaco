package fi.digitraffic.exceptions;

import fi.digitraffic.DigitrafficRuntimeException;

import java.io.IOException;

/**
 * Wraps an {@link java.io.IOException} which cannot be recovered from within the running system because of f.e. missing
 * file permissions.
 */
public class UnrecoverableIOException extends DigitrafficRuntimeException {

    public UnrecoverableIOException(String message, IOException cause) {
        super(message, cause);
    }

}
