package io.figchain.client.vault;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultBackup {

    private String version;
    private String keyFingerprint;
    private String encryptedKey;
    private String encryptedData;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getKeyFingerprint() {
        return keyFingerprint;
    }

    public void setKeyFingerprint(String keyFingerprint) {
        this.keyFingerprint = keyFingerprint;
    }

    public String getEncryptedKey() {
        return encryptedKey;
    }

    public void setEncryptedKey(String encryptedKey) {
        this.encryptedKey = encryptedKey;
    }

    public String getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
    }
}
