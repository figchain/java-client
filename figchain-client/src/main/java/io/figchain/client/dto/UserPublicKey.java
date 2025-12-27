package io.figchain.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserPublicKey {
    private String email;
    private String publicKey;
    private String algorithm;

    public UserPublicKey() {
    }

    public UserPublicKey(String email, String publicKey, String algorithm) {
        this.email = email;
        this.publicKey = publicKey;
        this.algorithm = algorithm;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
