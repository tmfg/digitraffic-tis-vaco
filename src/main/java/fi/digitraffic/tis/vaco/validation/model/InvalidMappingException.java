package fi.digitraffic.tis.vaco.validation.model;

import fi.digitraffic.tis.vaco.VacoException;

// TODO: move this to errorhandling?
public class InvalidMappingException extends VacoException {
    public InvalidMappingException(String message) {
        super(message);
    }

    public InvalidMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
