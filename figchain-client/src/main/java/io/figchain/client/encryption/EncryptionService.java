package io.figchain.client.encryption;

import io.figchain.avro.model.Fig;
import io.figchain.client.dto.NamespaceKey;
import io.figchain.client.transport.FcClientTransport;
import io.figchain.client.util.BufferUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;
import javax.crypto.KeyAgreement;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.KeySpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.InvalidAlgorithmParameterException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;

import java.security.PrivateKey;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class EncryptionService {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

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

        byte[] dek;
        try {
            dek = decryptAesGcm(wrappedDek, nsk);
        } catch (RuntimeException gcmEx) {
            throw new RuntimeException("Failed to unwrap DEK (AES-GCM)", gcmEx);
        }

        byte[] payload = BufferUtils.toByteArray(fig.getPayload());
        return decryptAesGcm(payload, dek);
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
            byte[] unwrappedNsk = decryptX25519(wrappedKeyBytes, privateKey);

            if (matchingKey.getKeyId() != null) {
                nskCache.put(matchingKey.getKeyId(), unwrappedNsk);
            }
            return unwrappedNsk;
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to decrypt NSK", e);
        }
    }

    public static byte[] decryptX25519(byte[] packedBlob, byte[] privateKeyBytes) {
        if (packedBlob.length < 32 + 12) {
            throw new IllegalArgumentException("Blob too short");
        }

        byte[] ephPub = new byte[32];
        System.arraycopy(packedBlob, 0, ephPub, 0, 32);

        byte[] iv = new byte[12];
        System.arraycopy(packedBlob, 32, iv, 0, 12);

        byte[] ciphertext = new byte[packedBlob.length - 44];
        System.arraycopy(packedBlob, 44, ciphertext, 0, ciphertext.length);

        try {
            KeyFactory kf = KeyFactory.getInstance("X25519");

            KeySpec privSpec = new XECPrivateKeySpec(new NamedParameterSpec("X25519"), privateKeyBytes);
            PrivateKey privKey = kf.generatePrivate(privSpec);

            byte[] uBytes = new byte[32];
            for (int i = 0; i < 32; i++) {
                uBytes[i] = ephPub[31 - i];
            }
            BigInteger u = new BigInteger(1, uBytes);
            KeySpec pubSpec = new XECPublicKeySpec(new NamedParameterSpec("X25519"), u);
            PublicKey pubKey = kf.generatePublic(pubSpec);

            KeyAgreement ka = KeyAgreement.getInstance("X25519");
            ka.init(privKey);
            ka.doPhase(pubKey, true);
            byte[] sharedSecret = ka.generateSecret();

            byte[] kek = hkdfSha256(sharedSecret);

            byte[] aesGcmInput = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, aesGcmInput, 0, iv.length);
            System.arraycopy(ciphertext, 0, aesGcmInput, iv.length, ciphertext.length);

            return decryptAesGcm(aesGcmInput, kek);

        } catch (Exception e) {
            throw new RuntimeException("X25519 decryption failed", e);
        }
    }

    public static byte[] hkdfSha256(byte[] ikm) {
        try {
            String algo = "HmacSHA256";
            byte[] salt = new byte[32];

            Mac mac = Mac.getInstance(algo);
            SecretKeySpec saltKey = new SecretKeySpec(salt, algo);
            mac.init(saltKey);
            byte[] prk = mac.doFinal(ikm);

            byte[] info = new byte[0];
            byte[] t = new byte[info.length + 1];
            System.arraycopy(info, 0, t, 0, info.length);
            t[t.length - 1] = (byte) 0x01;

            mac.init(new SecretKeySpec(prk, algo));
            byte[] okm = mac.doFinal(t);

            return okm;
        } catch (Exception e) {
            throw new RuntimeException("HKDF failed", e);
        }
    }

    public static byte[] decryptAesGcm(byte[] encryptedBytes, byte[] key) {
        try {
            if (encryptedBytes.length < IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Encrypted data too short");
            }

            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(encryptedBytes, 0, iv, 0, IV_LENGTH_BYTES);

            int ciphertextLength = encryptedBytes.length - IV_LENGTH_BYTES;
            byte[] ciphertext = new byte[ciphertextLength];
            System.arraycopy(encryptedBytes, IV_LENGTH_BYTES, ciphertext, 0, ciphertextLength);

            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            SecretKeySpec keySpec = new SecretKeySpec(key, AES_ALGORITHM);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            return cipher.doFinal(ciphertext);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Failed to decrypt AES GCM", e);
        }
    }
}
