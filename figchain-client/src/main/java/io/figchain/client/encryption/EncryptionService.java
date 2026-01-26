package io.figchain.client.encryption;

import io.figchain.avro.model.Fig;
import io.figchain.client.dto.NamespaceKey;
import io.figchain.client.transport.FcClientTransport;
import io.figchain.client.util.BufferUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class EncryptionService {

    private final FcClientTransport transport;
    private final byte[] privateKey;
    private final Map<String, byte[]> nskCache = new ConcurrentHashMap<>();

    private io.figchain.client.backup.S3EnvelopeProvider s3Provider;

    public EncryptionService(FcClientTransport transport, String privateKeyHex) {
        this.transport = transport;
        this.privateKey = java.util.HexFormat.of().parseHex(privateKeyHex);
    }

    // For direct byte array
    public EncryptionService(FcClientTransport transport, byte[] privateKeyBytes) {
        this.transport = transport;
        this.privateKey = privateKeyBytes;
    }

    public byte[] decrypt(Fig fig, String namespace) {
        if (!Boolean.TRUE.equals(fig.getIsEncrypted())) {
            return BufferUtils.toByteArray(fig.getPayload());
        }

        String nskId = fig.getKeyId() != null ? fig.getKeyId().toString() : null;
        byte[] wrappedDek = BufferUtils.toByteArray(fig.getWrappedDek());

        byte[] nsk = getNsk(namespace, nskId);

        // DEK unwrap (Always AES-GCM now)
        byte[] dek;
        try {
            dek = EncryptionCrypto.decryptAesGcm(wrappedDek, nsk);
        } catch (RuntimeException gcmEx) {
            throw new RuntimeException("Failed to unwrap DEK (AES-GCM)", gcmEx);
        }

        // Payload decrypt
        byte[] payload = BufferUtils.toByteArray(fig.getPayload());
        return EncryptionCrypto.decryptAesGcm(payload, dek);
    }

    public void setS3Provider(io.figchain.client.backup.S3EnvelopeProvider s3Provider) {
        this.s3Provider = s3Provider;
    }

    private byte[] getNsk(String namespace, String keyId) {
        if (keyId != null && nskCache.containsKey(keyId)) {
            return nskCache.get(keyId);
        }

        NamespaceKey matchingKey = null;

        // Try API
        try {
            java.util.List<NamespaceKey> nsKeys = transport.getNamespaceKey(namespace);
            matchingKey = nsKeys.stream()
                    .filter(k -> Objects.equals(keyId, k.getKeyId()))
                    .findFirst()
                    .orElse(null);

            // If explicit keyId not found, but we have keys and keyId was null, pick first?
            if (matchingKey == null && keyId == null && !nsKeys.isEmpty()) {
                matchingKey = nsKeys.get(0);
            }
        } catch (Exception e) {
            // API failed, proceed to S3 fallback if available
            if (s3Provider == null) {
                if (e instanceof RuntimeException) throw (RuntimeException)e;
                throw new RuntimeException("Failed to fetch NSK from API", e);
            }
        }

        // Fallback to S3 Backups
        if (matchingKey == null && s3Provider != null) {
            java.util.Optional<NamespaceKey> s3Key = s3Provider.getEnvelope(namespace);
            if (s3Key.isPresent()) {
                NamespaceKey k = s3Key.get();
                if (keyId == null || Objects.equals(keyId, k.getKeyId())) {
                    matchingKey = k;
                }
            }
        }

        if (matchingKey == null) {
            throw new RuntimeException("No matching key found for namespace " + namespace + " and keyId " + keyId + " (tried API and S3)");
        }

        try {
            byte[] wrappedKeyBytes = java.util.Base64.getDecoder().decode(matchingKey.getWrappedKey());
            byte[] unwrappedNsk = EncryptionCrypto.decryptX25519(wrappedKeyBytes, privateKey);

            if (matchingKey.getKeyId() != null) {
                nskCache.put(matchingKey.getKeyId(), unwrappedNsk);
            }
            return unwrappedNsk;
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to decrypt NSK", e);
        }
    }
}
