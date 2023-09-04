package fi.digitraffic.tis.vaco;

public class InvalidMappingException extends VacoException {
    public InvalidMappingException(String message) {
        super(message);
    }

    public InvalidMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
