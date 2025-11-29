package io.figchain.client.polling;

import io.figchain.client.transport.FcClientTransport;
import io.figchain.client.transport.LongPollingFcClientTransport;
import io.figchain.avro.model.UpdateFetchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.LinkedList;
import java.util.Queue;

public class LongPollingStrategy implements PollingStrategy {

    private static final Logger log = LoggerFactory.getLogger(LongPollingStrategy.class);

    // Rate limiting constants
    private static final int UPDATE_FREQUENCY_THRESHOLD = 3; // Number of updates to trigger throttling
    private static final long UPDATE_FREQUENCY_WINDOW_MS = 10_000; // 10 second window
    private static final long THROTTLE_DELAY_MS = 500; // Delay when throttling

    private final LongPollingFcClientTransport fcClientTransport;
    private final FcUpdateListener updateListener;
    private final ExecutorService fetchExecutor;
    private final Set<String> namespaces;
    private final Map<String, String> namespaceCursors;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Track update timestamps per namespace for rate limiting
    private final Map<String, Queue<Long>> namespaceUpdateTimestamps = new ConcurrentHashMap<>();

    public LongPollingStrategy(FcClientTransport fcClientTransport, FcUpdateListener updateListener, ExecutorService fetchExecutor, Set<String> namespaces, Map<String, String> namespaceCursors) {
        if (!(fcClientTransport instanceof LongPollingFcClientTransport)) {
            throw new IllegalArgumentException("LongPollingStrategy requires a LongPollingFcClientTransport");
        }
        this.fcClientTransport = (LongPollingFcClientTransport) fcClientTransport;
        this.updateListener = updateListener;
        this.fetchExecutor = fetchExecutor;
        this.namespaces = namespaces;
        this.namespaceCursors = namespaceCursors;

        // Initialize update timestamp tracking for each namespace
        namespaces.forEach(namespace ->
            namespaceUpdateTimestamps.put(namespace, new LinkedList<>())
        );
    }

    @Override
    public void start() {
        running.set(true);
        namespaces.forEach(this::startPollingForNamespace);
        log.info("Started long polling for namespaces: {}", namespaces);
    }

    @Override
    public void stop() {
        running.set(false);
        log.info("Stopped long polling.");
    }

    private void startPollingForNamespace(String namespace) {
        CompletableFuture.runAsync(() -> {
            while (running.get()) {
                try {
                    String cursor = namespaceCursors.get(namespace);
                    if (cursor == null) {
                        log.warn("No cursor found for namespace {}. Skipping long poll.", namespace);
                        break;
                    }
                    UpdateFetchResponse response = fcClientTransport.fetchUpdatesLongPollingSynchronous(namespace, cursor, 30);
                    if (response != null) {
                        boolean hasUpdates = response.getFigFamilies() != null && !response.getFigFamilies().isEmpty();

                        if (hasUpdates) {
                            updateListener.onUpdate(response.getFigFamilies());
                        }
                        namespaceCursors.put(namespace, response.getCursor().toString());
                        log.debug("Successfully fetched updates for namespace {}. New cursor: {}", namespace, response.getCursor());

                        // Apply adaptive throttling if there are frequent updates
                        applyThrottleIfNeeded(namespace, hasUpdates);
                    }
                } catch (Exception e) {
                    log.error("Failed to long poll for updates for namespace {}", namespace, e);
                    // Backoff before retrying
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }, fetchExecutor);
    }

    /**
     * Records an update timestamp and determines if throttling should be applied
     * based on recent update frequency.
     */
    private boolean shouldThrottle(String namespace, boolean hasUpdates) {
        if (!hasUpdates) {
            return false; // No throttling if there were no updates
        }

        Queue<Long> timestamps = namespaceUpdateTimestamps.get(namespace);
        if (timestamps == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        // Remove timestamps outside the window
        timestamps.removeIf(timestamp -> now - timestamp > UPDATE_FREQUENCY_WINDOW_MS);

        // Add current timestamp
        timestamps.offer(now);

        // Check if we've exceeded the threshold
        boolean shouldThrottle = timestamps.size() >= UPDATE_FREQUENCY_THRESHOLD;

        if (shouldThrottle) {
            log.debug("Throttling namespace {} due to {} updates in last {}ms",
                     namespace, timestamps.size(), UPDATE_FREQUENCY_WINDOW_MS);
        }

        return shouldThrottle;
    }

    /**
     * Applies throttling delay if needed
     */
    private void applyThrottleIfNeeded(String namespace, boolean hasUpdates) {
        if (shouldThrottle(namespace, hasUpdates)) {
            try {
                Thread.sleep(THROTTLE_DELAY_MS);
                log.debug("Applied {}ms throttle delay for namespace {}", THROTTLE_DELAY_MS, namespace);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
