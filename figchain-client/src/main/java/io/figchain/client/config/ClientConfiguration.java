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
    private String authClientId;
    private String tenantId;

    // Vault (S3 Backup) Configuration
    private boolean s3BackupEnabled = false;
    private String vaultBucket;
    private String vaultPrefix = "";
    private String vaultRegion = "us-east-1";
    private String vaultEndpoint;
    private boolean vaultPathStyleAccess = false;
    private String authPrivateKey;
    private String encryptionPrivateKey;
    private BootstrapMode bootstrapMode = BootstrapMode.SERVER_FIRST;

    public enum BootstrapMode {
        SERVER_FIRST,
        S3_BACKUP_FIRST,
        SERVER_ONLY,
        S3_BACKUP_ONLY
    }

    public String getAuthPrivateKey() {
        return authPrivateKey;
    }

    public void setAuthPrivateKey(String authPrivateKey) {
        this.authPrivateKey = authPrivateKey;
    }

    public String getEncryptionPrivateKey() {
        return encryptionPrivateKey;
    }

    public void setEncryptionPrivateKey(String encryptionPrivateKey) {
        this.encryptionPrivateKey = encryptionPrivateKey;
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

    public boolean isS3BackupEnabled() {
        return s3BackupEnabled;
    }

    public void setS3BackupEnabled(boolean s3BackupEnabled) {
        this.s3BackupEnabled = s3BackupEnabled;
    }

    public String getS3BackupBucket() {
        return vaultBucket;
    }

    public void setS3BackupBucket(String vaultBucket) {
        this.vaultBucket = vaultBucket;
    }

    public String getS3BackupPrefix() {
        return vaultPrefix;
    }

    public void setS3BackupPrefix(String vaultPrefix) {
        this.vaultPrefix = vaultPrefix;
    }

    public String getS3BackupRegion() {
        return vaultRegion;
    }

    public void setS3BackupRegion(String vaultRegion) {
        this.vaultRegion = vaultRegion;
    }

    public String getS3BackupEndpoint() {
        return vaultEndpoint;
    }

    public void setS3BackupEndpoint(String vaultEndpoint) {
        this.vaultEndpoint = vaultEndpoint;
    }

    public boolean isS3BackupPathStyleAccess() {
        return vaultPathStyleAccess;
    }

    public void setS3BackupPathStyleAccess(boolean vaultPathStyleAccess) {
        this.vaultPathStyleAccess = vaultPathStyleAccess;
    }

    public BootstrapMode getBootstrapMode() {
        return bootstrapMode;
    }

    public void setBootstrapMode(BootstrapMode bootstrapMode) {
        this.bootstrapMode = bootstrapMode;
    }

    public String getAuthClientId() {
        return authClientId;
    }
    public void setAuthClientId(String authClientId) {
        this.authClientId = authClientId;
    }
    public String getTenantId() {
        return tenantId;
    }
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
