
package io.figchain.client;

import io.figchain.avro.model.Fig;
import org.apache.avro.specific.SpecificRecord;
import io.figchain.client.store.FigStore;
import io.figchain.client.transport.FcClientTransport;
import io.figchain.avro.model.InitialFetchResponse;
import io.figchain.avro.model.InitialFetchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * The main client for interacting with the FigChain service.
 */
import io.figchain.client.polling.FcUpdateListener;
import io.figchain.client.polling.PollingStrategy;

/**
 * The {@code FcClient} is the main client for interacting with the FigChain
 * (FC) service.
 * <p>
 * It manages the lifecycle of fetching and updating configuration ({@code Fig})
 * data from the backend,
 * storing it locally, and evaluating rollouts for clients. The client supports
 * multiple namespaces,
 * retry logic, and asynchronous initial data fetching. It also provides methods
 * to retrieve evaluated
 * figs based on keys and evaluation contexts.
 * </p>
 *
 * <h2>Usage</h2>
 * 
 * <pre>{@code
 * FcClient client = new FcClient(...);
 * client.setPollingStrategy(...);
 * client.start();
 * Optional<Fig> fig = client.getFig("namespace", "key", context);
 * client.stop();
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. It uses concurrent data structures and
 * synchronization primitives
 * to coordinate background fetching and access to the fig store.
 * </p>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 * <li>{@link #start()} - Begins fetching data and starts polling for
 * updates.</li>
 * <li>{@link #stop()} - Shuts down background tasks and releases
 * resources.</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li>Supports multiple namespaces and environments.</li>
 * <li>Configurable retry logic for initial and ongoing fetches.</li>
 * <li>Asynchronous initial data fetch with blocking on first {@code getFig()}
 * call if needed.</li>
 * <li>Integration with custom polling strategies and transport
 * implementations.</li>
 * </ul>
 *
 * @see FigStore
 * @see RolloutEvaluator
 * @see FcClientTransport
 * @see PollingStrategy
 * @see EvaluationContext
 */
public class FcClient implements FcUpdateListener {
    private static final Logger log = LoggerFactory.getLogger(FcClient.class);

    private final FigStore figStore;
    private final RolloutEvaluator rolloutEvaluator;
    private final EvaluationContext defaultContext;
    private final FcClientTransport fcClientTransport;
    private final String asOfTimestamp;
    private final Set<String> namespaces;
    private final ExecutorService fetchExecutor;
    private final int maxRetries;
    private final long retryDelayMillis;
    private final Map<String, String> namespaceCursors;
    private final java.util.UUID environmentId;
    private PollingStrategy pollingStrategy;
    private final CountDownLatch initialFetchLatch = new CountDownLatch(1);
    private final AtomicBoolean started = new AtomicBoolean(false);

    public FcClient(FigStore figStore, RolloutEvaluator rolloutEvaluator, FcClientTransport fcClientTransport,
            String asOfTimestamp, Set<String> namespaces, ExecutorService fetchExecutor, int maxRetries,
            long retryDelayMillis, java.util.UUID environmentId) {
        this(figStore, rolloutEvaluator, fcClientTransport, asOfTimestamp, namespaces, fetchExecutor, maxRetries,
                retryDelayMillis, environmentId, null);
    }

    public FcClient(FigStore figStore, RolloutEvaluator rolloutEvaluator, FcClientTransport fcClientTransport,
            String asOfTimestamp, Set<String> namespaces, ExecutorService fetchExecutor, int maxRetries,
            long retryDelayMillis, java.util.UUID environmentId, EvaluationContext defaultContext) {
        if (namespaces == null || namespaces.isEmpty()) {
            throw new IllegalArgumentException("At least one namespace must be configured.");
        }
        this.figStore = figStore;
        this.rolloutEvaluator = rolloutEvaluator;
        this.fcClientTransport = fcClientTransport;
        this.asOfTimestamp = asOfTimestamp;
        this.namespaces = namespaces;
        this.fetchExecutor = fetchExecutor;
        this.maxRetries = maxRetries;
        this.retryDelayMillis = retryDelayMillis;
        this.namespaceCursors = new ConcurrentHashMap<>();
        this.environmentId = environmentId;
        this.defaultContext = defaultContext;
        log.info("FcClient initialized with namespaces: {}, maxRetries: {}, retryDelayMillis: {}", namespaces,
                maxRetries, retryDelayMillis);
    }

    public void setPollingStrategy(PollingStrategy pollingStrategy) {
        this.pollingStrategy = pollingStrategy;
    }

    public Map<String, String> getNamespaceCursors() {
        return namespaceCursors;
    }

    public CompletableFuture<Void> start() {
        if (started.compareAndSet(false, true)) {
            log.info("Starting FcClient...");
            return CompletableFuture.runAsync(() -> {
                try {
                    fetchInitialData();
                } finally {
                    initialFetchLatch.countDown();
                }
            }, fetchExecutor).thenRun(() -> {
                pollingStrategy.start();
                log.info("FcClient started.");
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    public void stop() {
        log.info("Shutting down FcClient...");
        pollingStrategy.stop();
        fcClientTransport.shutdown();
        log.info("Client transport shut down.");
        fetchExecutor.shutdown();
        try {
            if (!fetchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Fetch executor did not terminate gracefully within 5 seconds. Forcibly shutting down.");
                fetchExecutor.shutdownNow();
            }
            log.info("Fetch executor shut down.");
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for fetch executor to terminate.", e);
            fetchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("FcClient shut down complete.");
    }

    private void fetchInitialData() {
        log.debug("Fetching initial data for namespaces: {}", namespaces);
        namespaces.stream().map(namespace -> CompletableFuture.runAsync(() -> {
            executeWithRetry(() -> {
                log.info("Building InitialFetchRequest with namespace: {} and environmentId: {}", namespace,
                        environmentId);
                InitialFetchRequest request = InitialFetchRequest.newBuilder()
                        .setNamespace(namespace)
                        .setEnvironmentId(environmentId)
                        .setAsOfTimestamp(asOfTimestamp != null ? java.time.Instant.parse(asOfTimestamp) : null)
                        .build();

                if (log.isDebugEnabled()) {
                    log.debug("InitialFetchRequest for namespace {}: {}", namespace, request);
                }

                InitialFetchResponse response = fcClientTransport.fetchInitial(namespace, environmentId,
                        asOfTimestamp != null ? java.time.Instant.parse(asOfTimestamp) : null);

                if (log.isDebugEnabled()) {
                    log.debug("InitialFetchResponse for namespace {}: cursor={}, figFamilies.size={}",
                            namespace, response.getCursor(),
                            response.getFigFamilies() != null ? response.getFigFamilies().size() : 0);
                }

                onUpdate(response.getFigFamilies());
                namespaceCursors.put(namespace, response.getCursor().toString());
                log.info("Successfully fetched initial data for namespace {}. Current cursor: {}", namespace,
                        response.getCursor());
                return null;
            }, "fetch initial data for namespace " + namespace);
        }, fetchExecutor)).collect(Collectors.toList()).forEach(CompletableFuture::join);
    }

    @Override
    public void onUpdate(java.util.List<io.figchain.avro.model.FigFamily> figFamilies) {
        if (figFamilies != null && !figFamilies.isEmpty()) {
            log.debug("Updating fig store with {} new/updated fig families.", figFamilies.size());
            for (io.figchain.avro.model.FigFamily figFamily : figFamilies) {
                figStore.put(figFamily);
            }
        } else {
            log.debug("No fig families to update in the store.");
        }
    }

    private <T> T executeWithRetry(Callable<T> task, String taskName) {
        int attempts = 0;
        while (attempts <= maxRetries) {
            try {
                return task.call();
            } catch (Exception e) {
                // Check for FcTransportException to avoid retrying on auth errors
                io.figchain.client.transport.FcTransportException transportException = null;
                if (e instanceof io.figchain.client.transport.FcTransportException) {
                    transportException = (io.figchain.client.transport.FcTransportException) e;
                } else if (e.getCause() instanceof io.figchain.client.transport.FcTransportException) {
                    transportException = (io.figchain.client.transport.FcTransportException) e.getCause();
                }

                if (transportException != null) {
                    int status = transportException.getStatusCode();
                    if (status == 401 || status == 403) {
                        log.error("Authentication/Authorization failed for {}: {}. Please check your client secret and permissions.", taskName, transportException.getMessage());
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
                        log.error("Retry delay interrupted for {}.", taskName, ie);
                        break;
                    }
                } else {
                    log.error("All {} attempts to {} failed. Giving up. Error: {}", maxRetries + 1, taskName,
                            e.getMessage());
                    throw new RuntimeException("Failed to " + taskName + " after " + (maxRetries + 1) + " attempts", e);
                }
            }
        }
        return null; // Should not be reached
    }

    /**
     * Retrieves a fig based on its namespace, key and the evaluation context.
     *
     * @param namespace the namespace of the fig
     * @param key       the key of the fig
     * @param context   the evaluation context
     * @return an Optional containing the evaluated fig, or an empty Optional if not
     *         found
     */

    public Optional<Fig> getFig(String namespace, String key, EvaluationContext context) {
        awaitInitialFetch();
        EvaluationContext effectiveContext = (defaultContext != null) ? defaultContext.merge(context) : context;
        return figStore.getFigFamily(namespace, key)
                .flatMap(figFamily -> rolloutEvaluator.evaluate(figFamily, effectiveContext));
    }

    public Optional<Fig> getFig(String namespace, String key) {
        awaitInitialFetch();
        if (defaultContext == null)
            throw new IllegalStateException("No default context configured");
        return getFig(namespace, key, defaultContext);
    }

    public Optional<Fig> getFig(String key, EvaluationContext context) {
        awaitInitialFetch();
        if (namespaces.size() != 1)
            throw new IllegalStateException("Multiple namespaces configured; use getFig(namespace, key, context)");
        String namespace = namespaces.iterator().next();
        return getFig(namespace, key, context);
    }

    public Optional<Fig> getFig(String key) {
        awaitInitialFetch();
        if (defaultContext == null)
            throw new IllegalStateException("No default context configured");
        if (namespaces.size() != 1)
            throw new IllegalStateException("Multiple namespaces configured; use getFig(namespace, key)");
        String namespace = namespaces.iterator().next();
        return getFig(namespace, key, defaultContext);
    }

    public <T extends SpecificRecord> Optional<T> getFig(String key, Class<T> clazz) {
        return getFig(key, defaultContext, clazz);
    }

    public <T extends SpecificRecord> Optional<T> getFig(String key, EvaluationContext context, Class<T> clazz) {
        Optional<Fig> fig = getFig(key, context);
        if (fig.isPresent()) {
            try {
                ByteBuffer buffer = fig.get().getPayload();
                // Duplicate to avoid modifying the original buffer's position
                ByteBuffer duplicate = buffer.duplicate();
                byte[] bytes = new byte[duplicate.remaining()];
                duplicate.get(bytes);
                return Optional.of(AvroEncoding.deserializeWithSchema(bytes, clazz));
            } catch (IOException e) {
                log.error("Failed to deserialize fig for key: {}", key, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private void awaitInitialFetch() {
        try {
            initialFetchLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for initial fetch to complete", e);
        }
    }

    /**
     * Returns the environmentId used by this client.
     *
     * @return the environment ID
     */
    public java.util.UUID getEnvironmentId() {
        return environmentId;
    }

    /**
     * Returns the fig store.
     *
     * @return the fig store
     */
    public FigStore getFigStore() {
        return figStore;
    }

    public EvaluationContext getDefaultContext() {
        return defaultContext;
    }

    /**
     * Returns the as-of timestamp.
     *
     * @return the as-of timestamp
     */
    public String getAsOfTimestamp() {
        return asOfTimestamp;
    }

    /**
     * Returns the set of namespaces.
     *
     * @return the set of namespaces
     */
    public Set<String> getNamespaces() {
        return namespaces;
    }

    // For testing only: allow tests to release the latch so getFig() does not block
    void _testReleaseInitialFetchLatch() {
        initialFetchLatch.countDown();
    }
}
