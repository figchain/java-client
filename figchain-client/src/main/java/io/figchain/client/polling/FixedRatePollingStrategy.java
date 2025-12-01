package io.figchain.client.polling;

import io.figchain.client.transport.FcClientTransport;
import io.figchain.avro.model.UpdateFetchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FixedRatePollingStrategy implements PollingStrategy {

    private static final Logger log = LoggerFactory.getLogger(FixedRatePollingStrategy.class);

    private final FcClientTransport fcClientTransport;
    private final FcUpdateListener updateListener;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService fetchExecutor;
    private final long pollingInterval;
    private final Set<String> namespaces;
    private final Map<String, String> namespaceCursors;

    public FixedRatePollingStrategy(FcClientTransport fcClientTransport, FcUpdateListener updateListener, ScheduledExecutorService scheduler, ExecutorService fetchExecutor, long pollingInterval, Set<String> namespaces, Map<String, String> namespaceCursors) {
        this.fcClientTransport = fcClientTransport;
        this.updateListener = updateListener;
        this.scheduler = scheduler;
        this.fetchExecutor = fetchExecutor;
        this.pollingInterval = pollingInterval;
        this.namespaces = namespaces;
        this.namespaceCursors = namespaceCursors;
    }

    @Override
    public void start() {
        scheduler.scheduleAtFixedRate(this::fetchUpdates, pollingInterval, pollingInterval, TimeUnit.MILLISECONDS);
        log.info("Started fixed rate polling with interval: {} ms", pollingInterval);
    }

    @Override
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Stopped fixed rate polling.");
    }

    private void fetchUpdates() {
        log.debug("Fetching updates for namespaces: {}", namespaces);
        namespaces.stream().map(namespace -> CompletableFuture.runAsync(() -> {
            String cursor = namespaceCursors.get(namespace);
            if (cursor == null) {
                log.warn("No cursor found for namespace {}. Skipping update fetch.", namespace);
                return;
            }
            try {
                UpdateFetchResponse response = fcClientTransport.fetchUpdates(namespace, cursor);
                if (response.getFigFamilies() != null && !response.getFigFamilies().isEmpty()) {
                    updateListener.onUpdate(response.getFigFamilies());
                }
                namespaceCursors.put(namespace, response.getCursor().toString());
                log.debug("Successfully fetched updates for namespace {}. New cursor: {}", namespace, response.getCursor());
            } catch (Exception e) {
                log.error("Failed to fetch updates for namespace {}", namespace, e);
            }
        }, fetchExecutor)).collect(Collectors.toList()).forEach(CompletableFuture::join);
    }
}
