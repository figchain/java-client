package io.figchain.client.transport;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

/**
 * Token provider that generates JWTs signed with a private key.
 */
public class PrivateKeyTokenProvider implements TokenProvider {

    private final java.security.PrivateKey privateKey;
    private final String serviceAccountId;
    private final String tenantId;
    private final String namespace;
    private final String keyId;
    private final long tokenTtlMillis;

    public PrivateKeyTokenProvider(
            String privateKeyHex,
            String serviceAccountId,
            String tenantId,
            String namespace,
            String keyId) {
        this(privateKeyHex, serviceAccountId, tenantId, namespace, keyId, 600_000); // Default 10 minutes
    }

    public PrivateKeyTokenProvider(
            String privateKeyHex,
            String serviceAccountId,
            String tenantId,
            String namespace,
            String keyId,
            long tokenTtlMillis) {
        this.serviceAccountId = serviceAccountId;
        this.tenantId = tenantId;
        this.namespace = namespace;
        this.keyId = keyId;
        this.tokenTtlMillis = tokenTtlMillis;
        this.privateKey = loadEd25519PrivateKey(privateKeyHex);
    }

    private java.security.PrivateKey loadEd25519PrivateKey(String hexKey) {
        try {
            byte[] keyBytes = java.util.HexFormat.of().parseHex(hexKey);
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("Ed25519");
            java.security.spec.KeySpec spec = new java.security.spec.EdECPrivateKeySpec(
                new java.security.spec.NamedParameterSpec("Ed25519"), keyBytes);
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Ed25519 private key", e);
        }
    }

    @Override
    public String getToken() {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(tokenTtlMillis);

        com.auth0.jwt.JWTCreator.Builder builder = JWT.create()
                .withIssuer(serviceAccountId)
                .withSubject(serviceAccountId)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(exp))
                .withKeyId(keyId);

        if (tenantId != null) {
            builder.withClaim("tenant_id", tenantId);
        }
        if (namespace != null) {
            builder.withClaim("namespace", namespace);
        }

        // Pass null for public key as we are only signing
        try {
            return builder.sign(new Ed25519Algorithm((java.security.interfaces.EdECPrivateKey) privateKey));
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign token", e);
        }
    }
}
