package io.figchain.client.transport;

import io.figchain.client.FcApiClientException;

public class FcTransportException extends FcApiClientException {
    private final int statusCode;
    private final String responseBody;

    public FcTransportException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public FcTransportException(String message, Throwable cause, int statusCode, String responseBody) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public FcTransportException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
