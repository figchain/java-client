package io.figchain.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.figchain.client.config.ClientConfiguration;
import io.figchain.client.store.FigStore;
import io.figchain.client.store.MemoryFigStore;
import io.figchain.client.transport.FcClientTransport;

import io.figchain.client.polling.BroadcastFcUpdateListener;
import io.figchain.client.polling.FcUpdateListener;
import io.figchain.client.polling.FixedRatePollingStrategy;
import io.figchain.client.polling.LongPollingStrategy;
import io.figchain.client.polling.PollingStrategy;
import io.figchain.client.transport.HttpFcClientTransport;
import io.figchain.client.transport.LongPollingFcClientTransport;
import io.figchain.client.transport.Transport;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;

/**
 * A builder for creating {@link FigChainClient} instances.
 */
public class FigChainClientBuilder {

    private FigStore figStore;
    private RolloutEvaluator rolloutEvaluator;
    private String baseUrl = "https://app.figchain.io/api/";
    private String longPollingBaseUrl;
    private long pollingInterval = 60_000;
    private int maxRetries = 3;
    private long retryDelayMillis = 1000L;
    private String asOfTimestamp;
    private Set<String> namespaces = new HashSet<>();
    private java.util.UUID environmentId;
    private HttpClient httpClient;
    private String clientSecret;
    private Transport transport = Transport.LONG_POLLING;
    private final List<FcUpdateListener> updateListeners = new ArrayList<>();
    private EvaluationContext defaultContext = new EvaluationContext();

    /**
     * Sets the default evaluation context to use for getFig overloads.
     *
     * @param context the default context
     * @return this builder
     */
    public FigChainClientBuilder withDefaultContext(EvaluationContext context) {
        this.defaultContext = context;
        return this;
    }

    /**
     * Sets the fig store to use.
     *
     * @param figStore the fig store
     * @return this builder
     */
    public FigChainClientBuilder withFigStore(FigStore figStore) {
        this.figStore = figStore;
        return this;
    }

    /**
     * Sets the rollout evaluator to use.
     *
     * @param rolloutEvaluator the rollout evaluator
     * @return this builder
     */
    public FigChainClientBuilder withRolloutEvaluator(RolloutEvaluator rolloutEvaluator) {
        this.rolloutEvaluator = rolloutEvaluator;
        return this;
    }

    /**
     * Sets the base URL for the FigChain API.
     *
     * @param baseUrl the base URL
     * @return this builder
     */
    public FigChainClientBuilder withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * Sets the base URL for the long polling endpoint. If not set, the regular `baseUrl` will be used.
     *
     * @param longPollingBaseUrl the long polling base URL
     * @return this builder
     */
    public FigChainClientBuilder withLongPollingBaseUrl(String longPollingBaseUrl) {
        this.longPollingBaseUrl = longPollingBaseUrl;
        return this;
    }

    /**
     * Sets the polling interval in milliseconds.
     *
     * @param pollingInterval the polling interval
     * @return this builder
     */
    public FigChainClientBuilder withPollingInterval(long pollingInterval) {
        this.pollingInterval = pollingInterval;
        return this;
    }

    /**
     * Sets the maximum number of retries for failed transport calls.
     *
     * @param maxRetries the maximum number of retries
     * @return this builder
     */
    public FigChainClientBuilder withMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * Sets the delay between retries in milliseconds.
     *
     * @param retryDelayMillis the retry delay in milliseconds
     * @return this builder
     */
    public FigChainClientBuilder withRetryDelayMillis(long retryDelayMillis) {
        this.retryDelayMillis = retryDelayMillis;
        return this;
    }

    /**
     * Sets the as-of timestamp.
     *
     * @param asOfTimestamp the as-of timestamp
     * @return this builder
     */
    public FigChainClientBuilder withAsOfTimestamp(String asOfTimestamp) {
        this.asOfTimestamp = asOfTimestamp;
        return this;
    }

    /**
     * Sets the namespaces to fetch configurations for.
     *
     * @param namespaces the set of namespaces
     * @return this builder
     */
    public FigChainClientBuilder withNamespaces(Set<String> namespaces) {
        this.namespaces = new HashSet<>(namespaces);
        return this;
    }

    /**
     * Sets the environment ID for the FigChain service.
     *
     * @param environmentId the environment ID
     * @return this builder
     */
    public FigChainClientBuilder withEnvironmentId(java.util.UUID environmentId) {
        this.environmentId = environmentId;
        return this;
    }

    /**
     * Sets the OkHttpClient to use for network requests.
     *
     * @param httpClient the OkHttpClient instance
     * @return this builder
     */
    public FigChainClientBuilder withHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        return this;
    }

    /**
     * Sets the client secret for authentication.
     *
     * @param clientSecret the client secret
     * @return this builder
     */
    public FigChainClientBuilder withClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

    public FigChainClientBuilder withTransport(Transport transport) {
        this.transport = transport;
        return this;
    }

    public FigChainClientBuilder withUpdateListener(FcUpdateListener listener) {
        this.updateListeners.add(listener);
        return this;
    }

    /**
     * Loads configuration from a YAML file.
     *
     * @param filePath the path to the YAML file
     * @return this builder
     * @throws IOException if the file cannot be read
     */
    public FigChainClientBuilder fromYaml(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        ClientConfiguration config = mapper.readValue(new File(filePath), ClientConfiguration.class);
        if (config.getBaseUrl() != null) {
            this.baseUrl = config.getBaseUrl();
        }
        if (config.getLongPollingBaseUrl() != null) {
            this.longPollingBaseUrl = config.getLongPollingBaseUrl();
        }
        this.pollingInterval = config.getPollingIntervalMillis();
        this.maxRetries = config.getMaxRetries();
        this.retryDelayMillis = config.getRetryDelayMillis();
        this.asOfTimestamp = config.getAsOfTimestamp();
        this.namespaces = new HashSet<>(config.getNamespaces());
        this.clientSecret = config.getClientSecret();
        if (config.getEnvironmentId() != null) {
            this.environmentId = java.util.UUID.fromString(config.getEnvironmentId());
        }
        return this;
    }

    /**
     * Builds a new {@link FigChainClient} instance.
     *
     * @return a new client
     */
    public FigChainClient build() {
        if (baseUrl == null) {
            throw new IllegalStateException("baseUrl must be set");
        } if (environmentId == null) {
            throw new IllegalStateException("environmentId must be set");
        }

        if (figStore == null) {
            figStore = new MemoryFigStore();
        }
        if (rolloutEvaluator == null) {
            rolloutEvaluator = new RuleBasedRolloutEvaluator();
        }
        if (httpClient == null) {
            httpClient = java.net.http.HttpClient.newHttpClient();
        }

        URI baseUri = URI.create(this.baseUrl);

        FcClientTransport fcClientTransport;
        if (transport == Transport.LONG_POLLING) {
            URI lpUri = (longPollingBaseUrl != null) ? URI.create(longPollingBaseUrl) : baseUri;
            fcClientTransport = new LongPollingFcClientTransport(httpClient, baseUri, clientSecret, lpUri, environmentId);
        } else {
            fcClientTransport = new HttpFcClientTransport(httpClient, baseUri, clientSecret, environmentId);
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService fetchExecutor = Executors.newVirtualThreadPerTaskExecutor();

        final FigChainClient fcClient = new FigChainClient(figStore, rolloutEvaluator, fcClientTransport, asOfTimestamp, namespaces, fetchExecutor, maxRetries, retryDelayMillis, environmentId, defaultContext);
        this.updateListeners.add(fcClient);
        final FcUpdateListener broadcastListener = new BroadcastFcUpdateListener(this.updateListeners);

        PollingStrategy pollingStrategy;
        if (transport == Transport.LONG_POLLING) {
            pollingStrategy = new LongPollingStrategy(fcClientTransport, broadcastListener, fetchExecutor, namespaces, fcClient.getNamespaceCursors());
        } else {
            pollingStrategy = new FixedRatePollingStrategy(fcClientTransport, broadcastListener, scheduler, fetchExecutor, pollingInterval, namespaces, fcClient.getNamespaceCursors());
        }

        fcClient.setPollingStrategy(pollingStrategy);

        return fcClient;
    }
}
