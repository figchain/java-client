package io.figchain.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.figchain.avro.model.Fig;
import io.figchain.avro.model.FigFamily;
import io.figchain.client.bootstrap.BootstrapStrategy;
import io.figchain.client.polling.FcUpdateListener;
import io.figchain.client.polling.PollingStrategy;
import io.figchain.client.store.FigStore;
import io.figchain.client.transport.FcClientTransport;

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
public class FigChainClient implements FcUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(FigChainClient.class);

    private final FigStore figStore;
    private final RolloutEvaluator rolloutEvaluator;
    private final EvaluationContext defaultContext;
    private final FcClientTransport fcClientTransport;
    private final String asOfTimestamp;
    private final Set<String> namespaces;
    private final ExecutorService fetchExecutor;
    private final Map<String, String> namespaceCursors;
    private final java.util.UUID environmentId;
    private PollingStrategy pollingStrategy;
    private final BootstrapStrategy bootstrapStrategy;
    private final CountDownLatch initialFetchLatch = new CountDownLatch(1);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Map<String, List<TypedListener<?>>> typedListeners = new ConcurrentHashMap<>();

    private static class TypedListener<T extends SpecificRecord> {
        final Class<T> clazz;
        final EvaluationContext context;
        final Consumer<? super T> listener;

        TypedListener(Class<T> clazz, EvaluationContext context, Consumer<? super T> listener) {
            this.clazz = clazz;
            this.context = context;
            this.listener = listener;
        }
    }

    public FigChainClient(FigStore figStore, RolloutEvaluator rolloutEvaluator, FcClientTransport fcClientTransport,
            String asOfTimestamp, Set<String> namespaces, ExecutorService fetchExecutor, int maxRetries,
            long retryDelayMillis, java.util.UUID environmentId) {
        this(figStore, rolloutEvaluator, fcClientTransport, asOfTimestamp, namespaces, fetchExecutor, maxRetries,
                retryDelayMillis, environmentId, null);
    }

    public FigChainClient(FigStore figStore, RolloutEvaluator rolloutEvaluator, FcClientTransport fcClientTransport,
            String asOfTimestamp, Set<String> namespaces, ExecutorService fetchExecutor, int maxRetries,
            long retryDelayMillis, java.util.UUID environmentId, EvaluationContext defaultContext) {
        this(figStore, rolloutEvaluator, fcClientTransport, asOfTimestamp, namespaces, fetchExecutor, environmentId,
                new io.figchain.client.bootstrap.ServerBootstrapStrategy(fcClientTransport, environmentId, asOfTimestamp, maxRetries, retryDelayMillis),
                defaultContext);
    }

    public FigChainClient(FigStore figStore, RolloutEvaluator rolloutEvaluator, FcClientTransport fcClientTransport,
            String asOfTimestamp, Set<String> namespaces, ExecutorService fetchExecutor, int maxRetries,
            long retryDelayMillis, java.util.UUID environmentId, BootstrapStrategy bootstrapStrategy, EvaluationContext defaultContext) {
        if (namespaces == null || namespaces.isEmpty()) {
            throw new IllegalArgumentException("At least one namespace must be configured.");
        }
        this.figStore = figStore;
        this.rolloutEvaluator = rolloutEvaluator;
        this.fcClientTransport = fcClientTransport;
        this.asOfTimestamp = asOfTimestamp;
        this.namespaces = namespaces;
        this.fetchExecutor = fetchExecutor;
        this.namespaceCursors = new ConcurrentHashMap<>();
        this.environmentId = environmentId;
        this.bootstrapStrategy = bootstrapStrategy;
        this.defaultContext = defaultContext;
        log.info("FcClient initialized with namespaces: {}", namespaces);
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
                } catch (Exception e) {
                    log.error("Initial fetch failed", e);
                    throw new RuntimeException("Initial fetch failed", e);
                } finally {
                    initialFetchLatch.countDown();
                }
            }, fetchExecutor).thenRun(() -> {
                if (pollingStrategy != null) {
                    pollingStrategy.start();
                }
                log.info("FcClient started.");
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    public void stop() {
        log.info("Shutting down FcClient...");
        if (pollingStrategy != null) {
            pollingStrategy.stop();
        }
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
        try {
            log.debug("Bootstrapping data for namespaces: {}", namespaces);
            io.figchain.client.bootstrap.BootstrapResult result = bootstrapStrategy.bootstrap(namespaces);

            if (result != null) {
                onUpdate(result.getFigFamilies());
                if (result.getCursors() != null) {
                    namespaceCursors.putAll(result.getCursors());
                }
            }
            log.info("Bootstrap complete.");
        } catch (RuntimeException e) {
            log.error("Bootstrap failed", e);
            throw e;
        } catch (Exception e) {
            log.error("Bootstrap failed", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpdate(List<FigFamily> figFamilies) {
        if (figFamilies != null && !figFamilies.isEmpty()) {
            log.debug("Updating fig store with {} new/updated fig families.", figFamilies.size());
            for (FigFamily figFamily : figFamilies) {
                figStore.put(figFamily);
                notifyTypedListeners(figFamily);
            }
        } else {
            log.debug("No fig families to update in the store.");
        }
    }

    private void notifyTypedListeners(FigFamily figFamily) {
        String namespace = figFamily.getDefinition().getNamespace().toString();
        String key = figFamily.getDefinition().getKey().toString();
        String lookupKey = namespace + ":" + key;

        List<TypedListener<?>> listeners = typedListeners.get(lookupKey);
        if (listeners != null) {
            for (TypedListener<?> listener : listeners) {
                notifyListener(figFamily, listener);
            }
        }
    }

    private <T extends SpecificRecord> void notifyListener(FigFamily figFamily, TypedListener<T> listener) {
        try {
            EvaluationContext effectiveContext = (defaultContext != null) ? defaultContext.merge(listener.context) : (listener.context != null ? listener.context : new EvaluationContext());
            Optional<Fig> fig = rolloutEvaluator.evaluate(figFamily, effectiveContext);
            if (fig.isPresent()) {
                byte[] bytes = toByteArray(fig.get().getPayload());
                T decoded = AvroEncoding.deserializeBinary(bytes, listener.clazz);
                listener.listener.accept(decoded);
            }
        } catch (IOException | RuntimeException e) {
            log.error("Failed to notify listener for key: {}", figFamily.getDefinition().getKey(), e);
        }
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
                byte[] bytes = toByteArray(fig.get().getPayload());
                return Optional.of(AvroEncoding.deserializeBinary(bytes, clazz));
            } catch (IOException e) {
                log.error("Failed to deserialize fig for key: {}", key, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Registers a listener for updates to a specific Fig key.
     * <p>
     * WARNING: This feature should be used for SERVER-SCOPED configuration only (e.g. global flags).
     * The update is evaluated with the context provided at registration (or an empty one). If your rules depend on
     * user-specific attributes (like request-scoped context), this listener may receive default values or fail to match rules.
     * For request-scoped configuration, use getFig() with the appropriate context when needed.
     * </p>
     */
    public <T extends SpecificRecord> void registerListener(String key, Class<T> clazz, Consumer<? super T> listener) {
        if (namespaces.size() != 1) {
            throw new IllegalStateException("Multiple namespaces configured; use registerListener(namespace, key, clazz, listener)");
        }
        registerListener(namespaces.iterator().next(), key, null, clazz, listener);
    }

    public <T extends SpecificRecord> void registerListener(String key, EvaluationContext context, Class<T> clazz, Consumer<? super T> listener) {
        if (namespaces.size() != 1) {
            throw new IllegalStateException("Multiple namespaces configured; use registerListener(namespace, key, context, clazz, listener)");
        }
        registerListener(namespaces.iterator().next(), key, context, clazz, listener);
    }

    public <T extends SpecificRecord> void registerListener(String namespace, String key, Class<T> clazz, Consumer<? super T> listener) {
        registerListener(namespace, key, null, clazz, listener);
    }

    /**
     * Registers a listener for updates to a specific Fig key in a specific namespace.
     * <p>
     * WARNING: This feature should be used for SERVER-SCOPED configuration only (e.g. global flags).
     * The update is evaluated with the context provided at registration (or an empty one). If your rules depend on
     * user-specific attributes (like request-scoped context), this listener may receive default values or fail to match rules.
     * For request-scoped configuration, use getFig() with the appropriate context when needed.
     * </p>
     */
    public <T extends SpecificRecord> void registerListener(String namespace, String key, EvaluationContext context, Class<T> clazz, Consumer<? super T> listener) {
        String lookupKey = namespace + ":" + key;
        typedListeners.computeIfAbsent(lookupKey, k -> new CopyOnWriteArrayList<>())
                .add(new TypedListener<>(clazz, context, listener));
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

    private byte[] toByteArray(ByteBuffer buffer) {
        // Duplicate to avoid modifying the original buffer's position
        ByteBuffer duplicate = buffer.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }
}
