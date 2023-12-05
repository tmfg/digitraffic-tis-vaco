package fi.digitraffic.tis.vaco.db;

import fi.digitraffic.tis.vaco.VacoException;

/**
 * Thrown when expected entity does not exist in database. Implies either programming error or data corruption.
 */
public class UnknownEntityException extends VacoException {

    private final String identifier;

    public UnknownEntityException(String identifier, String message) {
        super(message + "'" + identifier + "'");
        this.identifier = identifier;
    }

    public UnknownEntityException(String identifier, String message, Throwable cause) {
        super(message + "'" + identifier + "'", cause);
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }
}
