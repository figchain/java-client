package io.figchain.client.polling;

import io.figchain.avro.model.UpdateFetchResponse;
import io.figchain.client.transport.FcClientTransport;
import io.figchain.client.transport.FcNetworkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Single update loop for both long polling and plain polling.
 * <p>
 * The two modes differ only in how long the server may hold each request
 * ({@link #LONG_POLL_WAIT} vs zero) and how long the client pauses before the next one
 * (zero vs the polling interval). Each namespace gets its own loop, so a long poll on a
 * quiet namespace never delays another's updates.
 * <p>
 * Long polling falls back to plain polling for a namespace after
 * {@link #LONG_POLL_FALLBACK_AFTER} consecutive in-transit failures (timeouts, dropped
 * connections), as happens behind proxies that cut long-held requests. Error responses from
 * the server don't count towards this.
 */
public class UpdatePollingStrategy implements PollingStrategy {

    private static final Logger log = LoggerFactory.getLogger(UpdatePollingStrategy.class);

    /** How long the server may hold a request; under common proxy idle timeouts (often 60s). */
    static final Duration LONG_POLL_WAIT = Duration.ofSeconds(30);
    static final int LONG_POLL_FALLBACK_AFTER = 3;

    // When long polling, briefly back off if a namespace sees a burst of updates.
    private static final int BURST_THRESHOLD = 3;
    private static final long BURST_WINDOW_MS = 10_000;
    private static final long BURST_DELAY_MS = 500;

    private final FcClientTransport transport;
    private final FcUpdateListener updateListener;
    private final ExecutorService executor;
    private final Set<String> namespaces;
    private final Map<String, String> namespaceCursors;
    private final boolean longPolling;
    private final long pollingIntervalMs;
    private final CountDownLatch stopped = new CountDownLatch(1);

    public UpdatePollingStrategy(FcClientTransport transport, FcUpdateListener updateListener, ExecutorService executor,
                                 Set<String> namespaces, Map<String, String> namespaceCursors,
                                 boolean longPolling, long pollingIntervalMs) {
        this.transport = transport;
        this.updateListener = updateListener;
        this.executor = executor;
        this.namespaces = namespaces;
        this.namespaceCursors = namespaceCursors;
        this.longPolling = longPolling;
        this.pollingIntervalMs = pollingIntervalMs;
    }

    @Override
    public void start() {
        namespaces.forEach(ns -> executor.execute(() -> pollNamespace(ns)));
        log.info("Started {} for namespaces: {}", longPolling ? "long polling" : "polling every " + pollingIntervalMs + "ms", namespaces);
    }

    @Override
    public void stop() {
        stopped.countDown();
        log.info("Stopped polling.");
    }

    private boolean isStopped() {
        return stopped.getCount() == 0;
    }

    void pollNamespace(String namespace) {
        boolean longPoll = longPolling;
        int transitFailures = 0;
        Deque<Long> recentUpdates = new ArrayDeque<>();

        while (!isStopped()) {
            String cursor = namespaceCursors.get(namespace);
            if (cursor == null) {
                log.warn("No cursor found for namespace {}. Not polling it.", namespace);
                return;
            }

            long pauseMs = pollingIntervalMs;
            try {
                UpdateFetchResponse response = transport.fetchUpdates(namespace, cursor, longPoll ? LONG_POLL_WAIT : Duration.ZERO);
                transitFailures = 0;
                boolean hasUpdates = apply(namespace, response);
                if (longPoll) {
                    // The server already waited; reconnect straight away unless updates are bursting.
                    pauseMs = isBurst(recentUpdates, hasUpdates) ? BURST_DELAY_MS : 0;
                }
            } catch (Exception e) {
                if (isStopped() || Thread.currentThread().isInterrupted()) {
                    return;
                }
                log.warn("Failed to fetch updates for namespace {}", namespace, e);
                if (longPoll && e instanceof FcNetworkException && ++transitFailures >= LONG_POLL_FALLBACK_AFTER) {
                    log.warn("Long polling for namespace {} failed {} times in transit; falling back to polling every {}ms",
                            namespace, transitFailures, pollingIntervalMs);
                    longPoll = false;
                }
            }

            if (pauseMs > 0) {
                try {
                    if (stopped.await(pauseMs, TimeUnit.MILLISECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Applies a response; returns whether it carried fig updates. */
    private boolean apply(String namespace, UpdateFetchResponse response) {
        boolean hasUpdates = response.getFigFamilies() != null && !response.getFigFamilies().isEmpty();
        boolean hasSchemas = response.getSchemas() != null && !response.getSchemas().isEmpty();
        if (hasUpdates || hasSchemas) {
            Map<String, String> schemaMap = new HashMap<>();
            if (hasSchemas) {
                response.getSchemas().forEach((k, v) -> schemaMap.put(k.toString(), v.toString()));
            }
            updateListener.onUpdate(response.getFigFamilies(), schemaMap);
        }
        if (response.getCursor() != null) {
            namespaceCursors.put(namespace, response.getCursor().toString());
        }
        log.debug("Fetched updates for namespace {}. New cursor: {}", namespace, response.getCursor());
        return hasUpdates;
    }

    private static boolean isBurst(Deque<Long> recentUpdates, boolean hasUpdates) {
        if (!hasUpdates) {
            return false;
        }
        long now = System.currentTimeMillis();
        while (!recentUpdates.isEmpty() && now - recentUpdates.peekFirst() > BURST_WINDOW_MS) {
            recentUpdates.pollFirst();
        }
        recentUpdates.addLast(now);
        return recentUpdates.size() >= BURST_THRESHOLD;
    }
}
