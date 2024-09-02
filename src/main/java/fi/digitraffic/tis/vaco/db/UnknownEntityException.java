package fi.digitraffic.tis.vaco.db;

import fi.digitraffic.tis.vaco.VacoException;

/**
 * Thrown when expected entity does not exist in database. Implies either programming error or data corruption.
 */
public class UnknownEntityException extends VacoException {

    public UnknownEntityException(String identifier, String message) {
        super(message + "'" + identifier + "'");
    }

}
