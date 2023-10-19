package fi.digitraffic.tis.http;

import fi.digitraffic.tis.exceptions.LibraryException;

class HttpClientException extends LibraryException {
    public HttpClientException(String message) {
        super(message);
    }

    public HttpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
