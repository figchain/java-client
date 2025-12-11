package io.figchain.client.config;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for the FcClient.
 */
public class ClientConfiguration {

    private String baseUrl;
    private String longPollingBaseUrl;
    private long pollingIntervalMillis = 60_000; // in milliseconds
    private int maxRetries = 3;
    private long retryDelayMillis = 1000L;
    private String asOfTimestamp;
    private Set<String> namespaces = new HashSet<>();
    private String clientSecret;
    private String environmentId;

    // Vault (S3 Backup) Configuration
    private boolean vaultEnabled = false;
    private String vaultBucket;
    private String vaultPrefix = "";
    private String vaultRegion = "us-east-1";
    private String vaultEndpoint;
    private boolean vaultPathStyleAccess = false;
    private String vaultPrivateKeyPath;
    private BootstrapMode bootstrapMode = BootstrapMode.SERVER_FIRST;

    public enum BootstrapMode {
        SERVER_FIRST,
        VAULT_FIRST,
        SERVER_ONLY,
        VAULT_ONLY
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getPollingIntervalMillis() {
        return pollingIntervalMillis;
    }

    public void setPollingIntervalMillis(long pollingIntervalMillis) {
        this.pollingIntervalMillis = pollingIntervalMillis;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryDelayMillis() {
        return retryDelayMillis;
    }

    public void setRetryDelayMillis(long retryDelayMillis) {
        this.retryDelayMillis = retryDelayMillis;
    }

    public String getAsOfTimestamp() {
        return asOfTimestamp;
    }

    public void setAsOfTimestamp(String asOfTimestamp) {
        this.asOfTimestamp = asOfTimestamp;
    }

    public Set<String> getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(Set<String> namespaces) {
        this.namespaces = namespaces;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getLongPollingBaseUrl() {
        return longPollingBaseUrl;
    }

    public void setLongPollingBaseUrl(String longPollingBaseUrl) {
        this.longPollingBaseUrl = longPollingBaseUrl;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
    }

    public boolean isVaultEnabled() {
        return vaultEnabled;
    }

    public void setVaultEnabled(boolean vaultEnabled) {
        this.vaultEnabled = vaultEnabled;
    }

    public String getVaultBucket() {
        return vaultBucket;
    }

    public void setVaultBucket(String vaultBucket) {
        this.vaultBucket = vaultBucket;
    }

    public String getVaultPrefix() {
        return vaultPrefix;
    }

    public void setVaultPrefix(String vaultPrefix) {
        this.vaultPrefix = vaultPrefix;
    }

    public String getVaultRegion() {
        return vaultRegion;
    }

    public void setVaultRegion(String vaultRegion) {
        this.vaultRegion = vaultRegion;
    }

    public String getVaultEndpoint() {
        return vaultEndpoint;
    }

    public void setVaultEndpoint(String vaultEndpoint) {
        this.vaultEndpoint = vaultEndpoint;
    }

    public boolean isVaultPathStyleAccess() {
        return vaultPathStyleAccess;
    }

    public void setVaultPathStyleAccess(boolean vaultPathStyleAccess) {
        this.vaultPathStyleAccess = vaultPathStyleAccess;
    }

    public String getVaultPrivateKeyPath() {
        return vaultPrivateKeyPath;
    }

    public void setVaultPrivateKeyPath(String vaultPrivateKeyPath) {
        this.vaultPrivateKeyPath = vaultPrivateKeyPath;
    }

    public BootstrapMode getBootstrapMode() {
        return bootstrapMode;
    }

    public void setBootstrapMode(BootstrapMode bootstrapMode) {
        this.bootstrapMode = bootstrapMode;
    }
}
