package io.figchain.client.transport;

public class FcAuthorizationException extends FcTransportException {
    public FcAuthorizationException(String message, String responseBody) {
        super(message, 403, responseBody);
    }

    public FcAuthorizationException(String message, Throwable cause, String responseBody) {
        super(message, cause, 403, responseBody);
    }
}
