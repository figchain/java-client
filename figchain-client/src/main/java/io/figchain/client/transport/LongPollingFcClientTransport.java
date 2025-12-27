package io.figchain.client.transport;

import io.figchain.avro.model.UpdateFetchRequest;
import io.figchain.avro.model.UpdateFetchResponse;
import io.figchain.client.AvroEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Long polling implementation of FcClientTransport.
 * Extends the regular HTTP transport with long polling capabilities for update requests.
 */
public class LongPollingFcClientTransport extends HttpFcClientTransport {

    private static final Logger log = LoggerFactory.getLogger(LongPollingFcClientTransport.class);

    private final URI longPollingBaseUrl;
    private final Duration longPollingTimeout;
    private final Duration connectionTimeout;



    /**
     * Performs a long polling update request that will block until updates are available
     * or the timeout is reached.
     */
    private final java.util.UUID environmentId;

    public LongPollingFcClientTransport(HttpClient httpClient, URI baseUrl, TokenProvider tokenProvider,
                                       URI longPollingBaseUrl, java.util.UUID environmentId) {
        super(httpClient, baseUrl, tokenProvider, environmentId);
        this.longPollingBaseUrl = longPollingBaseUrl;
        this.longPollingTimeout = Duration.ofSeconds(30); // 30 second long poll
        this.connectionTimeout = Duration.ofSeconds(35); // Slightly longer than long poll timeout
        this.environmentId = environmentId;
    }

    public CompletableFuture<UpdateFetchResponse> fetchUpdatesLongPolling(String namespace, String cursor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
        UpdateFetchRequest.Builder requestBuilder = UpdateFetchRequest.newBuilder()
            .setNamespace(namespace)
            .setCursor(cursor)
                        .setEnvironmentId(environmentId);
                UpdateFetchRequest request = requestBuilder.build();

                if (log.isDebugEnabled()) {
                    log.debug("UpdateFetchRequest (long polling) for namespace {}: {}", namespace, request);
                }

                byte[] avroBytes = AvroEncoding.serializeWithSchema(request);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(this.longPollingBaseUrl.resolve("data/updates"))
                        .header("Authorization", authHeaderValue())
                        .header("Content-Type", "application/octet-stream")
                        .header("X-Long-Poll-Timeout", String.valueOf(longPollingTimeout.toSeconds()))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(avroBytes))
                        .timeout(connectionTimeout)
                        .build();

                log.debug("Starting long poll request for namespace: {} with cursor: {}", namespace, cursor);
                HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 200) {
                    UpdateFetchResponse updateResponse = AvroEncoding.deserializeWithSchema(response.body(), UpdateFetchResponse.class);
                    if (log.isDebugEnabled()) {
                        log.debug("UpdateFetchResponse (long polling) for namespace {}: cursor={}, figFamilies.size={}",
                            namespace, updateResponse.getCursor(),
                            updateResponse.getFigFamilies() != null ? updateResponse.getFigFamilies().size() : 0);
                    }
                    log.debug("Long poll completed with {} updates for namespace: {}",
                            updateResponse.getFigFamilies() != null ? updateResponse.getFigFamilies().size() : 0, namespace);
                    return updateResponse;
                } else if (response.statusCode() == 204) {
                    // No content - timeout reached with no updates
                    log.debug("Long poll timeout reached for namespace: {} with cursor: {}", namespace, cursor);
                    UpdateFetchResponse.Builder responseBuilder = UpdateFetchResponse.newBuilder()
                            .setCursor(cursor)
                            .setFigFamilies(new java.util.ArrayList<>());
                    return responseBuilder.build();
                } else {
                    throw new IOException("Unexpected response code: " + response.statusCode());
                }
            } catch (IOException e) {
                log.error("Failed to fetch updates via long polling for namespace: {}", namespace, e);
                throw new RuntimeException(e);
            } catch (InterruptedException ex) {
                log.info("Long polling interrupted for namespace: {}", namespace);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Long polling interrupted", ex);
            }
        });
    }

    /**
     * Override to use long polling for updates instead of regular polling
     */
    @Override
    public UpdateFetchResponse fetchUpdates(String namespace, String cursor) {
        // Use long polling synchronously with default timeout
        return fetchUpdatesLongPollingSynchronous(namespace, cursor, longPollingTimeout.toSeconds());
    }

    /**
     * Synchronous long polling with timeout
     */
    public UpdateFetchResponse fetchUpdatesLongPollingSynchronous(String namespace, String cursor, long timeoutSeconds) {
        try {
            return fetchUpdatesLongPolling(namespace, cursor)
                    .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.debug("Long polling timed out after {} seconds for namespace: {}", timeoutSeconds, namespace);
            // Return empty response with original cursor
            return UpdateFetchResponse.newBuilder()
                    .setCursor(cursor)
                    .setFigFamilies(new java.util.ArrayList<>())
                    .build();
        } catch (InterruptedException e) {
            log.info("Long polling interrupted for namespace: {}", namespace);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Long polling interrupted", e);
        } catch (ExecutionException e) {
            log.error("Long polling failed for namespace: {}", namespace, e.getCause());
            throw new RuntimeException("Long polling failed", e.getCause());
        }
    }
}
