package io.figchain.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Envelope {
    private Key key;

    @JsonProperty("encryptedBlob")
    private String encryptedBlob;

    public Key getKey() {
        return key;
    }

    public void setKey(Key key) {
        this.key = key;
    }

    public String getEncryptedBlob() {
        return encryptedBlob;
    }

    public void setEncryptedBlob(String encryptedBlob) {
        this.encryptedBlob = encryptedBlob;
    }

    public static class Key {
        private String targetId;
        private String namespaceId;
        private Integer nskVersion;

        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
        public String getNamespaceId() { return namespaceId; }
        public void setNamespaceId(String namespaceId) { this.namespaceId = namespaceId; }
        public Integer getNskVersion() { return nskVersion; }
        public void setNskVersion(Integer nskVersion) { this.nskVersion = nskVersion; }
    }
}
