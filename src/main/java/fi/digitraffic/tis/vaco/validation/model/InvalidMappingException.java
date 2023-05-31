package fi.digitraffic.tis.vaco.validation.model;

import fi.digitraffic.tis.vaco.VacoException;

public class InvalidMappingException extends VacoException {
    public InvalidMappingException(String message) {
        super(message);
    }
}
