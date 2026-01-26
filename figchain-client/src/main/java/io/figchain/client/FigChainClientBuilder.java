package io.figchain.client;
import io.figchain.client.encryption.EncryptionService;
import io.figchain.client.transport.TokenProvider;
import io.figchain.client.transport.SharedSecretTokenProvider;
import io.figchain.client.transport.PrivateKeyTokenProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.figchain.client.backup.S3BackupService;
import io.figchain.client.backup.S3EnvelopeProvider;
import io.figchain.client.bootstrap.BootstrapStrategy;
import io.figchain.client.bootstrap.FallbackServerFirstStrategy;
import io.figchain.client.bootstrap.HybridS3BackupFirstStrategy;
import io.figchain.client.bootstrap.S3BackupBootstrapStrategy;
import io.figchain.client.bootstrap.ServerBootstrapStrategy;
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
    private boolean s3BackupEnabled = false;
    private String s3BackupBucket;
    private String s3BackupPrefix = "";
    private String s3BackupRegion = "us-east-1";
    private String s3BackupEndpoint;
    private boolean s3BackupPathStyleAccess = false;
    private String encryptionPrivateKey;
    private String authPrivateKey;
    private String authClientId;
    private String authCredentialId;
    private String tenantId;
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

    public FigChainClientBuilder withS3BackupEnabled(boolean s3BackupEnabled) {
        this.s3BackupEnabled = s3BackupEnabled;
        return this;
    }

    public FigChainClientBuilder withS3BackupBucket(String vaultBucket) {
        this.s3BackupBucket = vaultBucket;
        return this;
    }

    public FigChainClientBuilder withS3BackupPrefix(String vaultPrefix) {
        this.s3BackupPrefix = vaultPrefix;
        return this;
    }

    public FigChainClientBuilder withS3BackupRegion(String vaultRegion) {
        this.s3BackupRegion = vaultRegion;
        return this;
    }

    public FigChainClientBuilder withS3BackupEndpoint(String vaultEndpoint) {
        this.s3BackupEndpoint = vaultEndpoint;
        return this;
    }

    public FigChainClientBuilder withS3BackupPathStyleAccess(boolean vaultPathStyleAccess) {
        this.s3BackupPathStyleAccess = vaultPathStyleAccess;
        return this;
    }

    public FigChainClientBuilder withEncryptionPrivateKey(String encryptionPrivateKey) {
        this.encryptionPrivateKey = encryptionPrivateKey;
        return this;
    }

    public FigChainClientBuilder withBootstrapMode(ClientConfiguration.BootstrapMode bootstrapMode) {
        this.bootstrapMode = bootstrapMode;
        return this;
    }

    public FigChainClientBuilder withAuthClientId(String authClientId) {
        this.authClientId = authClientId;
        return this;
    }
    public FigChainClientBuilder withAuthCredentialId(String authCredentialId) {
        this.authCredentialId = authCredentialId;
        return this;
    }
    public FigChainClientBuilder withAuthPrivateKey(String authPrivateKey) {
        this.authPrivateKey = authPrivateKey;
        return this;
    }
    public FigChainClientBuilder withTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }


    /**
     * Loads configuration from a JSON configuration file (client-config.json).
     *
     * @param filePath the path to the JSON file
     * @return this builder
     * @throws IOException if the file cannot be read or parsed
     */
    public FigChainClientBuilder fromConfig(java.nio.file.Path filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(); // Default mapper handles JSON
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(filePath.toFile());

        if (node.has("namespace")) {
            this.namespaces.add(node.get("namespace").asText());
        }
        if (node.has("namespaces") && node.get("namespaces").isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode nsNode : node.get("namespaces")) {
                this.namespaces.add(nsNode.asText());
            }
        }
        if (node.has("credentialId")) {
            this.authCredentialId = node.get("credentialId").asText();
        }
        if (node.has("privateKey")) {
            this.authPrivateKey = node.get("privateKey").asText();
        }
        if (node.has("authPrivateKey")) {
            this.authPrivateKey = node.get("authPrivateKey").asText();
        }
        if (node.has("encryptionPrivateKey")) {
            this.encryptionPrivateKey = node.get("encryptionPrivateKey").asText();
        }
        if (node.has("tenantId")) {
            this.tenantId = node.get("tenantId").asText();
        }
        if (node.has("environmentId")) {
            this.environmentId = java.util.UUID.fromString(node.get("environmentId").asText());
        }
        if (node.has("s3BackupEnabled")) {
            this.s3BackupEnabled = node.get("s3BackupEnabled").asBoolean();
        }
        if (node.has("s3BackupBucket")) {
            this.s3BackupBucket = node.get("s3BackupBucket").asText();
        }
        if (node.has("s3BackupPrefix")) {
            this.s3BackupPrefix = node.get("s3BackupPrefix").asText();
        }
        if (node.has("s3BackupRegion")) {
            this.s3BackupRegion = node.get("s3BackupRegion").asText();
        }
        if (node.has("s3BackupEndpoint")) {
            this.s3BackupEndpoint = node.get("s3BackupEndpoint").asText();
        }
        if (node.has("bootstrapMode")) {
             try {
                 this.bootstrapMode = ClientConfiguration.BootstrapMode.valueOf(node.get("bootstrapMode").asText());
             } catch (Exception e) {
                 // ignore or log
             }
        }

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
        this.s3BackupEnabled = config.isS3BackupEnabled();
        this.s3BackupBucket = config.getS3BackupBucket();
        this.s3BackupPrefix = config.getS3BackupPrefix();
        this.s3BackupRegion = config.getS3BackupRegion();
        this.s3BackupEndpoint = config.getS3BackupEndpoint();
        this.s3BackupPathStyleAccess = config.isS3BackupPathStyleAccess();
        this.encryptionPrivateKey = config.getEncryptionPrivateKey();
        if (config.getAuthPrivateKey() != null) {
            this.authPrivateKey = config.getAuthPrivateKey();
        }
        this.authClientId = config.getAuthClientId();
        this.tenantId = config.getTenantId();
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
     *   <li>FIGCHAIN_S3_BACKUP_ENABLED</li>
     *   <li>FIGCHAIN_S3_BACKUP_BUCKET</li>
     *   <li>FIGCHAIN_S3_BACKUP_PREFIX</li>
     *   <li>FIGCHAIN_S3_BACKUP_REGION</li>
     *   <li>FIGCHAIN_S3_BACKUP_ENDPOINT</li>
     *   <li>FIGCHAIN_S3_BACKUP_PATH_STYLE_ACCESS</li>
     *   <li>FIGCHAIN_S3_BACKUP_PRIVATE_KEY_PATH</li>
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
        } else {
            String envNamespace = envProvider.apply("FIGCHAIN_NAMESPACE");
            if (envNamespace != null && !envNamespace.trim().isEmpty()) {
                this.namespaces.add(envNamespace.trim());
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

        String envVaultEnabled = envProvider.apply("FIGCHAIN_S3_BACKUP_ENABLED");
        if (envVaultEnabled != null) this.s3BackupEnabled = Boolean.parseBoolean(envVaultEnabled);

        String envVaultBucket = envProvider.apply("FIGCHAIN_S3_BACKUP_BUCKET");
        if (envVaultBucket != null) this.s3BackupBucket = envVaultBucket;

        String envVaultPrefix = envProvider.apply("FIGCHAIN_S3_BACKUP_PREFIX");
        if (envVaultPrefix != null) this.s3BackupPrefix = envVaultPrefix;

        String envVaultRegion = envProvider.apply("FIGCHAIN_S3_BACKUP_REGION");
        if (envVaultRegion != null) this.s3BackupRegion = envVaultRegion;

        String envVaultEndpoint = envProvider.apply("FIGCHAIN_S3_BACKUP_ENDPOINT");
        if (envVaultEndpoint != null) this.s3BackupEndpoint = envVaultEndpoint;

        String envVaultPathStyle = envProvider.apply("FIGCHAIN_S3_BACKUP_PATH_STYLE_ACCESS");
        if (envVaultPathStyle != null) this.s3BackupPathStyleAccess = Boolean.parseBoolean(envVaultPathStyle);


        String envEncKey = envProvider.apply("FIGCHAIN_ENCRYPTION_PRIVATE_KEY");
        if (envEncKey != null) this.encryptionPrivateKey = envEncKey;

        String envAuthKey = envProvider.apply("FIGCHAIN_IDENTITY_PRIVATE_KEY"); // Using IDENTITY to match other clients
        if (envAuthKey != null) this.authPrivateKey = envAuthKey;
        String envAuthClientId = envProvider.apply("FIGCHAIN_AUTH_CLIENT_ID");
        if (envAuthClientId != null) this.authClientId = envAuthClientId;
        String envTenantId = envProvider.apply("FIGCHAIN_TENANT_ID");
        if (envTenantId != null) this.tenantId = envTenantId;

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
        if (clientSecret == null && authPrivateKey == null) {
            throw new IllegalStateException("An authentication method must be configured. Please provide either a clientSecret or authPrivateKey.");
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

        if (this.baseUrl != null && !this.baseUrl.endsWith("/")) {
            this.baseUrl = this.baseUrl + "/";
        }

        URI baseUri = URI.create(this.baseUrl);

        TokenProvider tokenProvider = null;
        if (authPrivateKey != null) {
            if (namespaces.size() > 1) {
                throw new IllegalStateException("Private key authentication can only be used with a single namespace");
            }
            String serviceAccountId = (authClientId != null) ? authClientId
                    : (authCredentialId != null) ? authCredentialId : environmentId.toString();
            String namespace = namespaces.isEmpty() ? null : namespaces.iterator().next();
            String authKeyHex = authPrivateKey.trim();
            tokenProvider = new PrivateKeyTokenProvider(authKeyHex, serviceAccountId, tenantId, namespace, authCredentialId);
        } else {
            tokenProvider = new SharedSecretTokenProvider(clientSecret);
        }

        FcClientTransport fcClientTransport;
        if (transport == Transport.LONG_POLLING) {
            URI lpUri = (longPollingBaseUrl != null) ? URI.create(longPollingBaseUrl) : baseUri;
            fcClientTransport = new LongPollingFcClientTransport(httpClient, baseUri, tokenProvider, lpUri, environmentId);
        } else {
            fcClientTransport = new HttpFcClientTransport(httpClient, baseUri, tokenProvider, environmentId);
        }

        // Configure Bootstrap Strategy
        BootstrapStrategy bootstrapStrategy;

        // 1. Server Strategy (Core)
        ServerBootstrapStrategy serverStrategy =
            new ServerBootstrapStrategy(fcClientTransport, environmentId, asOfTimestamp, maxRetries, retryDelayMillis);

        if (s3BackupEnabled) {
            ClientConfiguration s3Config = new ClientConfiguration();
            s3Config.setS3BackupEnabled(true);
            s3Config.setS3BackupBucket(s3BackupBucket);
            s3Config.setS3BackupPrefix(s3BackupPrefix);
            s3Config.setS3BackupRegion(s3BackupRegion);
            s3Config.setS3BackupEndpoint(s3BackupEndpoint);
            s3Config.setS3BackupPathStyleAccess(s3BackupPathStyleAccess);

            String s3Key = (authPrivateKey != null) ? authPrivateKey : encryptionPrivateKey;
            S3BackupService s3Service = new S3BackupService(s3Config, new ObjectMapper(), s3Key);
            S3BackupBootstrapStrategy s3Strategy = new S3BackupBootstrapStrategy(s3Service);

            if (bootstrapMode == ClientConfiguration.BootstrapMode.S3_BACKUP_ONLY) {
                bootstrapStrategy = s3Strategy;
            } else if (bootstrapMode == ClientConfiguration.BootstrapMode.S3_BACKUP_FIRST) {
                bootstrapStrategy = new HybridS3BackupFirstStrategy(s3Strategy, serverStrategy, fcClientTransport);
            } else {
                bootstrapStrategy = new FallbackServerFirstStrategy(serverStrategy, s3Strategy);
            }
        } else {
            bootstrapStrategy = serverStrategy;
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService fetchExecutor = Executors.newVirtualThreadPerTaskExecutor();

        EncryptionService encryptionService = null;
        if (encryptionPrivateKey != null) {
             encryptionService = new EncryptionService(fcClientTransport, encryptionPrivateKey.trim());
        } else if (authPrivateKey != null) {
            try {
                encryptionService = new EncryptionService(fcClientTransport, authPrivateKey.trim());
            } catch (Exception e) {
            }
        }

        if (encryptionService != null && s3BackupEnabled && s3BackupBucket != null) {
            String effectiveClientId = (authCredentialId != null) ? authCredentialId : authClientId;
            if (effectiveClientId != null) {
                String vAccessKey = System.getenv("FIGCHAIN_S3_BACKUP_ACCESS_KEY");
                String vSecretKey = System.getenv("FIGCHAIN_S3_BACKUP_SECRET_KEY");

                S3EnvelopeProvider s3Provider = new S3EnvelopeProvider(
                        s3BackupBucket, s3BackupPrefix, s3BackupRegion, vAccessKey, vSecretKey, s3BackupEndpoint, s3BackupPathStyleAccess, effectiveClientId
                );
                encryptionService.setS3Provider(s3Provider);
            }
        }

        final FigChainClient fcClient = new FigChainClient(figStore, rolloutEvaluator, fcClientTransport, asOfTimestamp, namespaces, fetchExecutor, environmentId, bootstrapStrategy, defaultContext, encryptionService);
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
