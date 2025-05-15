package fi.digitraffic.tis.vaco.webhooks;

import fi.digitraffic.tis.vaco.VacoException;

public class CacheLoadingException extends VacoException {
    public CacheLoadingException(String message) {
        super(message);
    }
}
