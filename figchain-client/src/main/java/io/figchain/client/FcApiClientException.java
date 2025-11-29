package io.figchain.client;

/**
 * Exception class for handling errors in the FC API client.
 */
public class FcApiClientException extends RuntimeException {

    /**
     * Constructs a new FcApiClientException with the specified detail message.
     *
     * @param message the detail message
     */
    public FcApiClientException(String message) {
        super(message);
    }

    /**
     * Constructs a new FcApiClientException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public FcApiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}