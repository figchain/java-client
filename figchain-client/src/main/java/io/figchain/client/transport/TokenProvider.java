package io.figchain.client.transport;

import java.io.IOException;

/**
 * Interface for providing authentication tokens.
 */
public interface TokenProvider {
    /**
     * Gets a valid authentication token.
     *
     * @return the token string
     * @throws IOException if token generation fails
     */
    String getToken() throws IOException;
}
