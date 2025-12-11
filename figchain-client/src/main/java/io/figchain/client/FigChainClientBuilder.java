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
    private boolean vaultEnabled = false;
    private String vaultBucket;
    private String vaultPrefix = "";
    private String vaultRegion = "us-east-1";
    private String vaultEndpoint;
    private boolean vaultPathStyleAccess = false;
    private String vaultPrivateKeyPath;
    private ClientConfiguration.BootstrapMode bootstrapMode = ClientConfiguration.BootstrapMode.SERVER_FIRST;

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

    public FigChainClientBuilder withVaultEnabled(boolean vaultEnabled) {
        this.vaultEnabled = vaultEnabled;
        return this;
    }

    public FigChainClientBuilder withVaultBucket(String vaultBucket) {
        this.vaultBucket = vaultBucket;
        return this;
    }

    public FigChainClientBuilder withVaultPrefix(String vaultPrefix) {
        this.vaultPrefix = vaultPrefix;
        return this;
    }

    public FigChainClientBuilder withVaultRegion(String vaultRegion) {
        this.vaultRegion = vaultRegion;
        return this;
    }

    public FigChainClientBuilder withVaultEndpoint(String vaultEndpoint) {
        this.vaultEndpoint = vaultEndpoint;
        return this;
    }

    public FigChainClientBuilder withVaultPathStyleAccess(boolean vaultPathStyleAccess) {
        this.vaultPathStyleAccess = vaultPathStyleAccess;
        return this;
    }

    public FigChainClientBuilder withVaultPrivateKeyPath(String vaultPrivateKeyPath) {
        this.vaultPrivateKeyPath = vaultPrivateKeyPath;
        return this;
    }

    public FigChainClientBuilder withBootstrapMode(ClientConfiguration.BootstrapMode bootstrapMode) {
        this.bootstrapMode = bootstrapMode;
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

        this.vaultEnabled = config.isVaultEnabled();
        this.vaultBucket = config.getVaultBucket();
        this.vaultPrefix = config.getVaultPrefix();
        this.vaultRegion = config.getVaultRegion();
        this.vaultEndpoint = config.getVaultEndpoint();
        this.vaultPathStyleAccess = config.isVaultPathStyleAccess();
        this.vaultPrivateKeyPath = config.getVaultPrivateKeyPath();
        this.bootstrapMode = config.getBootstrapMode();

        return this;
    }

    /**
     * Loads configuration from environment variables.
     * <p>
     * Supported environment variables:
     * <ul>
     *   <li>FIGCHAIN_URL</li>
     *   <li>FIGCHAIN_LONG_POLLING_URL</li>
     *   <li>FIGCHAIN_CLIENT_SECRET</li>
     *   <li>FIGCHAIN_ENVIRONMENT_ID</li>
     *   <li>FIGCHAIN_NAMESPACES (comma-separated)</li>
     *   <li>FIGCHAIN_POLLING_INTERVAL_MS</li>
     *   <li>FIGCHAIN_MAX_RETRIES</li>
     *   <li>FIGCHAIN_RETRY_DELAY_MS</li>
     *   <li>FIGCHAIN_AS_OF_TIMESTAMP</li>
     *   <li>FIGCHAIN_VAULT_ENABLED</li>
     *   <li>FIGCHAIN_VAULT_BUCKET</li>
     *   <li>FIGCHAIN_VAULT_PREFIX</li>
     *   <li>FIGCHAIN_VAULT_REGION</li>
     *   <li>FIGCHAIN_VAULT_ENDPOINT</li>
     *   <li>FIGCHAIN_VAULT_PATH_STYLE_ACCESS</li>
     *   <li>FIGCHAIN_VAULT_PRIVATE_KEY_PATH</li>
     *   <li>FIGCHAIN_BOOTSTRAP_MODE</li>
     * </ul>
     *
     * @return this builder
     */
    public FigChainClientBuilder fromEnv() {
        return fromEnv(System::getenv);
    }

    /**
     * Loads configuration from the provided environment variable provider.
     *
     * @param envProvider a function that provides the value for an environment variable
     * @return this builder
     */
    public FigChainClientBuilder fromEnv(java.util.function.Function<String, String> envProvider) {
        String envBaseUrl = envProvider.apply("FIGCHAIN_URL");
        if (envBaseUrl != null) this.baseUrl = envBaseUrl;

        String envLongPollingUrl = envProvider.apply("FIGCHAIN_LONG_POLLING_URL");
        if (envLongPollingUrl != null) this.longPollingBaseUrl = envLongPollingUrl;

        String envClientSecret = envProvider.apply("FIGCHAIN_CLIENT_SECRET");
        if (envClientSecret != null) this.clientSecret = envClientSecret;

        String envEnvId = envProvider.apply("FIGCHAIN_ENVIRONMENT_ID");
        if (envEnvId != null) this.environmentId = java.util.UUID.fromString(envEnvId);

        String envNamespaces = envProvider.apply("FIGCHAIN_NAMESPACES");
        if (envNamespaces != null && !envNamespaces.trim().isEmpty()) {
            String[] ns = envNamespaces.split(",");
            for (String n : ns) {
                if (!n.trim().isEmpty()) {
                    this.namespaces.add(n.trim());
                }
            }
        }

        String envPollingInterval = envProvider.apply("FIGCHAIN_POLLING_INTERVAL_MS");
        if (envPollingInterval != null) this.pollingInterval = Long.parseLong(envPollingInterval);

        String envMaxRetries = envProvider.apply("FIGCHAIN_MAX_RETRIES");
        if (envMaxRetries != null) this.maxRetries = Integer.parseInt(envMaxRetries);

        String envRetryDelay = envProvider.apply("FIGCHAIN_RETRY_DELAY_MS");
        if (envRetryDelay != null) this.retryDelayMillis = Long.parseLong(envRetryDelay);

        String envAsOf = envProvider.apply("FIGCHAIN_AS_OF_TIMESTAMP");
        if (envAsOf != null) this.asOfTimestamp = envAsOf;

        String envVaultEnabled = envProvider.apply("FIGCHAIN_VAULT_ENABLED");
        if (envVaultEnabled != null) this.vaultEnabled = Boolean.parseBoolean(envVaultEnabled);

        String envVaultBucket = envProvider.apply("FIGCHAIN_VAULT_BUCKET");
        if (envVaultBucket != null) this.vaultBucket = envVaultBucket;

        String envVaultPrefix = envProvider.apply("FIGCHAIN_VAULT_PREFIX");
        if (envVaultPrefix != null) this.vaultPrefix = envVaultPrefix;

        String envVaultRegion = envProvider.apply("FIGCHAIN_VAULT_REGION");
        if (envVaultRegion != null) this.vaultRegion = envVaultRegion;

        String envVaultEndpoint = envProvider.apply("FIGCHAIN_VAULT_ENDPOINT");
        if (envVaultEndpoint != null) this.vaultEndpoint = envVaultEndpoint;

        String envVaultPathStyle = envProvider.apply("FIGCHAIN_VAULT_PATH_STYLE_ACCESS");
        if (envVaultPathStyle != null) this.vaultPathStyleAccess = Boolean.parseBoolean(envVaultPathStyle);

        String envVaultKeyPath = envProvider.apply("FIGCHAIN_VAULT_PRIVATE_KEY_PATH");
        if (envVaultKeyPath != null) this.vaultPrivateKeyPath = envVaultKeyPath;

        String envBootstrapMode = envProvider.apply("FIGCHAIN_BOOTSTRAP_MODE");
        if (envBootstrapMode != null) this.bootstrapMode = ClientConfiguration.BootstrapMode.valueOf(envBootstrapMode);

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

        // Configure Bootstrap Strategy
        io.figchain.client.bootstrap.BootstrapStrategy bootstrapStrategy = null;

        // 1. Server Strategy (Core)
        io.figchain.client.bootstrap.ServerBootstrapStrategy serverStrategy =
            new io.figchain.client.bootstrap.ServerBootstrapStrategy(fcClientTransport, environmentId, asOfTimestamp, maxRetries, retryDelayMillis);

        if (vaultEnabled) {
            // Create Config object for VaultService (reuse fields from builder not ClientConfiguration object to avoid mismatch)
            ClientConfiguration vaultConfig = new ClientConfiguration();
            vaultConfig.setVaultEnabled(true); // Since we entered this block
            vaultConfig.setVaultBucket(vaultBucket);
            vaultConfig.setVaultPrefix(vaultPrefix);
            vaultConfig.setVaultRegion(vaultRegion);
            vaultConfig.setVaultEndpoint(vaultEndpoint);
            vaultConfig.setVaultPathStyleAccess(vaultPathStyleAccess);
            vaultConfig.setVaultPrivateKeyPath(vaultPrivateKeyPath);

            io.figchain.client.vault.VaultService vaultService = new io.figchain.client.vault.VaultService(vaultConfig, new ObjectMapper());
            io.figchain.client.bootstrap.VaultBootstrapStrategy vaultStrategy = new io.figchain.client.bootstrap.VaultBootstrapStrategy(vaultService);

            if (bootstrapMode == ClientConfiguration.BootstrapMode.VAULT_ONLY) {
                bootstrapStrategy = vaultStrategy;
            } else if (bootstrapMode == ClientConfiguration.BootstrapMode.VAULT_FIRST) {
                bootstrapStrategy = new io.figchain.client.bootstrap.HybridVaultFirstStrategy(vaultStrategy, serverStrategy, fcClientTransport);
            } else {
                // Default to SERVER_FIRST with fallback
                bootstrapStrategy = new io.figchain.client.bootstrap.FallbackServerFirstStrategy(serverStrategy, vaultStrategy);
            }
        } else {
            // Default to Server Only (standard behavior)
            bootstrapStrategy = serverStrategy;
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService fetchExecutor = Executors.newVirtualThreadPerTaskExecutor();

        final FigChainClient fcClient = new FigChainClient(figStore, rolloutEvaluator, fcClientTransport, asOfTimestamp, namespaces, fetchExecutor, environmentId, bootstrapStrategy, defaultContext);
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
