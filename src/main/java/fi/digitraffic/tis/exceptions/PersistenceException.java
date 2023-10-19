package fi.digitraffic.tis.exceptions;

import fi.digitraffic.tis.TisException;

public class PersistenceException extends TisException {
    public PersistenceException(String message) {
        super(message);
    }
}
