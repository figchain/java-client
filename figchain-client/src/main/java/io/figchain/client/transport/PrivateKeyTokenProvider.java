package io.figchain.client.transport;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Token provider that generates JWTs signed with a private key.
 */
public class PrivateKeyTokenProvider implements TokenProvider {
    private static final Logger log = LoggerFactory.getLogger(PrivateKeyTokenProvider.class);

    private final RSAPrivateKey privateKey;
    private final String serviceAccountId;
    private final String tenantId;
    private final String namespace;
    private final String keyId;
    private final long tokenTtlMillis;

    public PrivateKeyTokenProvider(String privateKeyPath, String serviceAccountId, String tenantId, String namespace, String keyId) throws IOException {
        this(privateKeyPath, serviceAccountId, tenantId, namespace, keyId, 600_000); // Default 10 minutes
    }

    public PrivateKeyTokenProvider(String privateKeyPath, String serviceAccountId, String tenantId, String namespace, String keyId, long tokenTtlMillis) throws IOException {
        this.serviceAccountId = serviceAccountId;
        this.tenantId = tenantId;
        this.namespace = namespace;
        this.keyId = keyId;
        this.tokenTtlMillis = tokenTtlMillis;
        this.privateKey = loadPrivateKey(privateKeyPath);
    }

    private RSAPrivateKey loadPrivateKey(String path) throws IOException {
        return io.figchain.client.util.KeyUtils.loadPrivateKey(java.nio.file.Path.of(path));
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

        return builder.sign(Algorithm.RSA256(null, privateKey));
    }
}
