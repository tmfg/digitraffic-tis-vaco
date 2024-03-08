package fi.digitraffic.http;

import fi.digitraffic.exceptions.LibraryException;

public class HttpClientException extends LibraryException {
    public HttpClientException(String message) {
        super(message);
    }

    public HttpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
