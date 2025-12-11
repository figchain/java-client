package io.figchain.client.bootstrap;

import io.figchain.avro.model.FigFamily;
import io.figchain.avro.model.InitialFetchResponse;
import io.figchain.client.transport.FcAuthenticationException;
import io.figchain.client.transport.FcAuthorizationException;
import io.figchain.client.transport.FcClientTransport;
import io.figchain.client.transport.FcTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

public class ServerBootstrapStrategy implements BootstrapStrategy {

    private static final Logger log = LoggerFactory.getLogger(ServerBootstrapStrategy.class);

    private final FcClientTransport transport;
    private final UUID environmentId;
    private final String asOfTimestamp;
    private final int maxRetries;
    private final long retryDelayMillis;

    public ServerBootstrapStrategy(FcClientTransport transport, UUID environmentId, String asOfTimestamp, int maxRetries, long retryDelayMillis) {
        this.transport = transport;
        this.environmentId = environmentId;
        this.asOfTimestamp = asOfTimestamp;
        this.maxRetries = maxRetries;
        this.retryDelayMillis = retryDelayMillis;
    }

    @Override
    public BootstrapResult bootstrap(Set<String> namespaces) throws Exception {
        log.debug("Bootstrapping from server for namespaces: {}", namespaces);
        List<FigFamily> allFamilies = new ArrayList<>();
        Map<String, String> cursors = new HashMap<>();

        for (String namespace : namespaces) {
            InitialFetchResponse response = executeWithRetry(() -> {
                log.info("Fetching initial data for namespace: {}", namespace);
                return transport.fetchInitial(namespace, environmentId,
                        asOfTimestamp != null ? Instant.parse(asOfTimestamp) : null);
            }, "fetch initial data for namespace " + namespace);

            if (response != null) {
                if (response.getFigFamilies() != null) {
                    allFamilies.addAll(response.getFigFamilies());
                }
                if (response.getCursor() != null) {
                    cursors.put(namespace, response.getCursor().toString());
                }
            }
        }

        return new BootstrapResult(allFamilies, cursors);
    }

    private <T> T executeWithRetry(Callable<T> task, String taskName) throws Exception {
        int attempts = 0;
        Exception lastException = null;
        while (attempts <= maxRetries) {
            try {
                return task.call();
            } catch (Exception e) {
                lastException = e;

                // Check for FcTransportException to avoid retrying on auth errors
                FcTransportException transportException = null;
                if (e instanceof FcTransportException) {
                    transportException = (FcTransportException) e;
                } else if (e.getCause() instanceof FcTransportException) {
                    transportException = (FcTransportException) e.getCause();
                }

                if (transportException != null) {
                    if (transportException instanceof FcAuthenticationException ||
                        transportException instanceof FcAuthorizationException) {
                        log.error("Authentication/Authorization failed for {}: {}", taskName, transportException.getMessage());
                        throw transportException;
                    }
                }

                attempts++;
                if (attempts <= maxRetries) {
                    log.warn("Attempt {}/{} to {} failed. Retrying in {} ms. Error: {}", attempts, maxRetries, taskName,
                            retryDelayMillis, e.getMessage());
                    try {
                        Thread.sleep(retryDelayMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                }
            }
        }
        throw new RuntimeException("Failed to " + taskName + " after " + (maxRetries + 1) + " attempts", lastException);
    }
}
