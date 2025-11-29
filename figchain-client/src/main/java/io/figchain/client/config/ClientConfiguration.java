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
}
