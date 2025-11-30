package io.figchain.client.transport;

import io.figchain.client.FcApiClientException;

public class FcTransportException extends FcApiClientException {
    private final int statusCode;

    public FcTransportException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public FcTransportException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
