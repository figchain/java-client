package io.figchain.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NamespaceKey {
    private String wrappedKey;
    private String keyId;

    public NamespaceKey() {
    }

    public NamespaceKey(String wrappedKey, String keyId) {
        this.wrappedKey = wrappedKey;
        this.keyId = keyId;
    }

    public String getWrappedKey() {
        return wrappedKey;
    }

    public void setWrappedKey(String wrappedKey) {
        this.wrappedKey = wrappedKey;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }
}
