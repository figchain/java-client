package io.figchain.client.transport;

import io.figchain.avro.model.InitialFetchResponse;
import io.figchain.avro.model.UpdateFetchResponse;
import io.figchain.client.dto.NamespaceKey;
import io.figchain.client.dto.UserPublicKey;

public interface FcClientTransport {

    /**
     * Fetches initial data for the client. This is a blocking operation.
     * @param namespace the namespace to fetch from
     * @param environmentId the environment ID
     * @param asOfTimestamp the as-of timestamp
     * @return the initial fetch response
     */
    InitialFetchResponse fetchInitial(String namespace, java.util.UUID environmentId, java.time.Instant asOfTimestamp);

    /**
     * Fetches any updates since the last fetch, whether it was an initial fetch
     * or another update.
     * @param namespace the namespace to fetch from
     * @param cursor the cursor given in the last response
     * @return the update fetch response
     */
    UpdateFetchResponse fetchUpdates(String namespace, String cursor);

    /**
     * Fetches updates after {@code cursor}, letting the server hold the request open for up
     * to {@code wait} until updates arrive (long polling). {@link java.time.Duration#ZERO}
     * returns immediately, like {@link #fetchUpdates(String, String)}.
     */
    default UpdateFetchResponse fetchUpdates(String namespace, String cursor, java.time.Duration wait) {
        return fetchUpdates(namespace, cursor);
    }

    /**
     * Fetches the Namespace Key for a given namespace.
     * @param namespace the namespace
     * @return the namespace key
     */
    java.util.List<NamespaceKey> getNamespaceKey(String namespace);

    /**
     * Uploads the user's public key to the server.
     * @param key the public key
     */
    void uploadPublicKey(UserPublicKey key);

    /**
     * Fetches a schema by namespace, name, and version.
     * @param namespace the namespace
     * @param name the schema name
     * @param version the schema version
     * @return the schema content
     */
    String fetchSchema(String namespace, String name, int version);

    /**
     * Shuts down the client cleanly.
     */
    void shutdown();
}
