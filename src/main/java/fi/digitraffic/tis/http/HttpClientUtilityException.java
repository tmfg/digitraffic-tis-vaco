package fi.digitraffic.tis.http;

import fi.digitraffic.tis.TisException;

class HttpClientUtilityException extends TisException {
    public HttpClientUtilityException(String message) {
        super(message);
    }

    public HttpClientUtilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
