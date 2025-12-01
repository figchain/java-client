package io.figchain.client.transport;

public class FcAuthenticationException extends FcTransportException {
    public FcAuthenticationException(String message, String responseBody) {
        super(message, 401, responseBody);
    }

    public FcAuthenticationException(String message, Throwable cause, String responseBody) {
        super(message, cause, 401, responseBody);
    }
}
