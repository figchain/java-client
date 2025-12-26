package io.figchain.client.encryption;

import io.figchain.avro.model.Fig;
import io.figchain.client.dto.NamespaceKey;
import io.figchain.client.transport.FcClientTransport;


import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EncryptionService {

    private final FcClientTransport transport;
    private final PrivateKey privateKey;
    private final Map<String, byte[]> nskCache = new ConcurrentHashMap<>();

    public EncryptionService(FcClientTransport transport, Path privateKeyPath) {
        this.transport = transport;
        try {
            this.privateKey = EncryptionCrypto.loadPrivateKey(privateKeyPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load private key from " + privateKeyPath, e);
        }
    }

    public byte[] decrypt(Fig fig, String namespace) {
        if (!Boolean.TRUE.equals(fig.getIsEncrypted())) {
            return toByteArray(fig.getPayload());
        }

        String nskId = fig.getKeyId() != null ? fig.getKeyId().toString() : null;
        byte[] wrappedDek = toByteArray(fig.getWrappedDek());

        byte[] nsk = getNsk(namespace, nskId);

        // DEK unwrap
        byte[] dek = EncryptionCrypto.unwrapAesKey(wrappedDek, nsk);

        // Payload decrypt
        byte[] payload = toByteArray(fig.getPayload());
        return EncryptionCrypto.decryptAesGcm(payload, dek);
    }

    private byte[] getNsk(String namespace, String keyId) {
        if (keyId != null && nskCache.containsKey(keyId)) {
            return nskCache.get(keyId);
        }

        java.util.List<NamespaceKey> nsKeys = transport.getNamespaceKey(namespace);

        NamespaceKey matchingKey = nsKeys.stream()
                .filter(k -> (keyId == null && k.getKeyId() == null) || (keyId != null && keyId.equals(k.getKeyId())))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No matching key found for namespace " + namespace + " and keyId " + keyId));

        try {
            byte[] wrappedKeyBytes = java.util.Base64.getDecoder().decode(matchingKey.getWrappedKey());
            byte[] unwrappedNsk = EncryptionCrypto.decryptRsaOaep(wrappedKeyBytes, privateKey);

            if (matchingKey.getKeyId() != null) {
                nskCache.put(matchingKey.getKeyId(), unwrappedNsk);
            }
            return unwrappedNsk;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt NSK", e);
        }
    }

    private byte[] toByteArray(ByteBuffer buffer) {
        if (buffer == null) return null;
        ByteBuffer duplicate = buffer.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }
}
