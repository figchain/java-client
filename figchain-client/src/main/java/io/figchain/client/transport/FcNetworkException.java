package io.figchain.client.transport;

import io.figchain.client.FcApiClientException;

public class FcNetworkException extends FcApiClientException {
    public FcNetworkException(String message) {
        super(message);
    }

    public FcNetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
