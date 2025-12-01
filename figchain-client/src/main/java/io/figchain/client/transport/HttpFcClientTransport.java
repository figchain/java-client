package io.figchain.client.transport;

import io.figchain.avro.model.InitialFetchRequest;
import io.figchain.avro.model.InitialFetchResponse;
import io.figchain.avro.model.UpdateFetchRequest;
import io.figchain.avro.model.UpdateFetchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import io.figchain.client.AvroEncoding;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpFcClientTransport implements FcClientTransport {

    private static final Logger log = LoggerFactory.getLogger(HttpFcClientTransport.class);

    protected final HttpClient httpClient;
    private final URI baseUrl;
    private final String clientSecret;
    private final java.util.UUID environmentId;

    protected String authHeaderValue() {
        return "Bearer " + clientSecret;
    }

    @Override
    public InitialFetchResponse fetchInitial(String namespace, java.util.UUID environmentId, java.time.Instant asOfTimestamp) {
        try {
            InitialFetchRequest.Builder requestBuilder = InitialFetchRequest.newBuilder()
                    .setNamespace(namespace)
                    .setEnvironmentId(environmentId);
            if (asOfTimestamp != null) {
                requestBuilder.setAsOfTimestamp(asOfTimestamp);
            }
            InitialFetchRequest request = requestBuilder.build();

            if (log.isDebugEnabled()) {
                log.debug("InitialFetchRequest (transport) for namespace {}: {}", namespace, request);
            }

            byte[] avroBytes = AvroEncoding.serializeWithSchema(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(baseUrl.resolve("data/initial"))
                    .header("Authorization", authHeaderValue())
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(avroBytes))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

            if (log.isDebugEnabled()) {
                log.debug("HTTP response for initial fetch: status={}, body.length={} bytes",
                    response.statusCode(), response.body() != null ? response.body().length : 0);
            }

            if (response.statusCode() != 200) {
                handleNon200Response(response);
            }
            InitialFetchResponse initialResponse = AvroEncoding.deserializeWithSchema(response.body(), InitialFetchResponse.class);

            if (log.isDebugEnabled()) {
                log.debug("InitialFetchResponse (transport) for namespace {}: cursor={}, figFamilies.size={}",
                    namespace, initialResponse.getCursor(),
                    initialResponse.getFigFamilies() != null ? initialResponse.getFigFamilies().size() : 0);
            }

            return initialResponse;
        } catch (IOException e) {
            log.error("Failed to fetch initial data", e);
            throw new FcNetworkException("Failed to fetch initial data", e);
        } catch (InterruptedException ex) {
            log.info("Client interrupted");
            throw new RuntimeException("Client interrupted", ex);
        }
    }

    public HttpFcClientTransport(HttpClient httpClient, URI baseUrl, String clientSecret) {
        this(httpClient, baseUrl, clientSecret, null);
    }

    public HttpFcClientTransport(HttpClient httpClient, URI baseUrl, String clientSecret, java.util.UUID environmentId) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.clientSecret = clientSecret;
        if (environmentId == null) {
            throw new IllegalArgumentException("environmentId must not be null");
        }
        this.environmentId = environmentId;

        if (log.isDebugEnabled()) {
            log.debug("HttpFcClientTransport configured with baseUrl: {}, environmentId: {}", this.baseUrl, environmentId);
        }
    }

    @Override
    public UpdateFetchResponse fetchUpdates(String namespace, String cursor) {
        try {
        UpdateFetchRequest.Builder requestBuilder = UpdateFetchRequest.newBuilder()
            .setNamespace(namespace)
            .setCursor(cursor)
            .setEnvironmentId(environmentId);
            UpdateFetchRequest request = requestBuilder.build();

            if (log.isDebugEnabled()) {
                log.debug("UpdateFetchRequest for namespace {}: {}", namespace, request);
            }

            byte[] avroBytes = AvroEncoding.serializeWithSchema(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(this.baseUrl.resolve("/data/updates"))
                    .header("Authorization", authHeaderValue())
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(avroBytes))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

            if (log.isDebugEnabled()) {
                log.debug("HTTP response for updates fetch: status={}, body.length={} bytes",
                    response.statusCode(), response.body() != null ? response.body().length : 0);
            }

            if (response.statusCode() != 200) {
                handleNon200Response(response);
            }
            // Response decoding already uses Avro container files
            UpdateFetchResponse updateResponse = AvroEncoding.deserializeWithSchema(response.body(), UpdateFetchResponse.class);

            if (log.isDebugEnabled()) {
                log.debug("UpdateFetchResponse for namespace {}: cursor={}, figFamilies.size={}",
                    namespace, updateResponse.getCursor(),
                    updateResponse.getFigFamilies() != null ? updateResponse.getFigFamilies().size() : 0);
            }

            return updateResponse;
        } catch (IOException e) {
            log.error("Failed to fetch updates", e);
            throw new FcNetworkException("Failed to fetch updates", e);
        } catch (InterruptedException ex) {
            log.info("Client interrupted");
            throw new RuntimeException("Client interrupted", ex);
        }
    }

    private void handleNon200Response(HttpResponse<byte[]> response) {
        String bodyString = null;
        if (response.body() != null && response.body().length > 0) {
            try {
                bodyString = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("Failed to decode response body to string", e);
            }
        }

        if (response.statusCode() == 401) {
            throw new FcAuthenticationException("Authentication failed. Please check your client secret.", bodyString);
        } else if (response.statusCode() == 403) {
            throw new FcAuthorizationException("Authorization failed. Please check that your environmentId, namespace, and client secret are correct.", bodyString);
        }

        String errorMsg = "Unexpected code " + response.statusCode();
        if (bodyString != null) {
            if (bodyString.length() > 1000) {
                errorMsg += ". Body: " + bodyString.substring(0, 1000) + "...";
            } else {
                errorMsg += ". Body: " + bodyString;
            }
        }
        throw new FcTransportException(errorMsg, response.statusCode(), bodyString);
    }

    @Override
    public void shutdown() {
    }
}
