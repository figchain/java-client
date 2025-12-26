package io.figchain.client.transport;

/**
 * Token provider that uses a static shared secret.
 */
public class SharedSecretTokenProvider implements TokenProvider {
    private final String clientSecret;

    public SharedSecretTokenProvider(String clientSecret) {
        if (clientSecret == null) {
            throw new IllegalArgumentException("Client secret must not be null");
        }
        this.clientSecret = clientSecret;
    }

    @Override
    public String getToken() {
        return clientSecret;
    }
}
