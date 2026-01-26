package io.figchain.client.encryption;

import io.figchain.client.transport.FcClientTransport;
import io.figchain.client.util.BufferUtils;
import io.figchain.avro.model.Fig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class EncryptionServiceTest {

    @Test
    public void testDecrypt() throws Exception {
        // Setup Keys
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
        KeyPair bobKp = kpg.generateKeyPair();

        // Get Bob's private key as Hex String (what the Service expects)
        byte[] bobPrivBytes = getPrivateKeyBytes(bobKp.getPrivate());
        String bobPrivHex = HexFormat.of().formatHex(bobPrivBytes);

        FcClientTransport transport = mock(FcClientTransport.class);
        EncryptionService service = new EncryptionService(transport, bobPrivHex);

        // Simulate Encrypted Fig from Server (Alice)
        KeyPair aliceKp = kpg.generateKeyPair();

        // Initialize KEK

        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(aliceKp.getPrivate());
        ka.doPhase(bobKp.getPublic(), true);
        byte[] sharedSecret = ka.generateSecret();
        byte[] kek = hkdfSha256(sharedSecret);

        // Server encrypts NSK with KEK
        byte[] nsk = new byte[32];
        new java.security.SecureRandom().nextBytes(nsk);

        byte[] nskIv = new byte[12];
        new java.security.SecureRandom().nextBytes(nskIv);
        byte[] encryptedNsk = encryptAesGcm(nsk, kek, nskIv);

        // Pack WrappedNSK: AlicePub || IV || EncryptedNSK
        byte[] alicePub = getPublicKeyBytes(aliceKp.getPublic());
        byte[] wrappedNskBytes = new byte[32 + 12 + encryptedNsk.length];
        System.arraycopy(alicePub, 0, wrappedNskBytes, 0, 32);
        System.arraycopy(nskIv, 0, wrappedNskBytes, 32, 12);
        System.arraycopy(encryptedNsk, 0, wrappedNskBytes, 44, encryptedNsk.length);

        String wrappedNskBase64 = java.util.Base64.getEncoder().encodeToString(wrappedNskBytes);

        io.figchain.client.dto.NamespaceKey dummyKey = new io.figchain.client.dto.NamespaceKey();
        dummyKey.setKeyId("some-key-id");
        dummyKey.setWrappedKey(wrappedNskBase64);

        Mockito.when(transport.getNamespaceKey("test-ns")).thenReturn(java.util.List.of(dummyKey));

        // Now encrypt DEK using NSK (which Client will unwrap)
        byte[] dek = new byte[32];
        new java.security.SecureRandom().nextBytes(dek);

        byte[] dekIv = new byte[12];
        new java.security.SecureRandom().nextBytes(dekIv);
        byte[] encryptedDek = encryptAesGcm(dek, nsk, dekIv);

        // Pack WrappedKey (for Fig): IV || EncryptedDEK (Symmetric wrap by NSK)
        // Note: No AlicePub for symmetric wrap.
        byte[] wrappedKeyBytes = new byte[12 + encryptedDek.length];
        System.arraycopy(dekIv, 0, wrappedKeyBytes, 0, 12);
        System.arraycopy(encryptedDek, 0, wrappedKeyBytes, 12, encryptedDek.length);

        // Encrypt Payload with DEK
        byte[] payload = "Secret Config".getBytes(StandardCharsets.UTF_8);
        byte[] payloadIv = new byte[12];
        new java.security.SecureRandom().nextBytes(payloadIv);
        byte[] encryptedPayload = encryptAesGcm(payload, dek, payloadIv);

        // Construct Encrypted Payload Blob for Fig: IV || Ciphertext
        byte[] figPayloadForStore = new byte[12 + encryptedPayload.length];
        System.arraycopy(payloadIv, 0, figPayloadForStore, 0, 12);
        System.arraycopy(encryptedPayload, 0, figPayloadForStore, 12, encryptedPayload.length);

        // Create Fig
        Fig fig = Fig.newBuilder()
                .setFigId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .setVersion(java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .setIsEncrypted(true)
                .setWrappedDek(ByteBuffer.wrap(wrappedKeyBytes))
                .setPayload(ByteBuffer.wrap(figPayloadForStore))
                .build();

        // Decrypt
        byte[] result = service.decrypt(fig, "test-ns");
        assertArrayEquals(payload, result);
    }

    private byte[] encryptAesGcm(byte[] plaintext, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
        return cipher.doFinal(plaintext);
    }

    private byte[] getPrivateKeyBytes(java.security.PrivateKey k) throws Exception {
        if (k instanceof java.security.interfaces.XECPrivateKey) {
             java.security.interfaces.XECPrivateKey xec = (java.security.interfaces.XECPrivateKey) k;
             return xec.getScalar().orElseThrow();
        }
        throw new IllegalArgumentException("Not XECPrivateKey");
    }

    private byte[] getPublicKeyBytes(java.security.PublicKey k) throws Exception {
        if (k instanceof java.security.interfaces.XECPublicKey) {
             java.security.interfaces.XECPublicKey xec = (java.security.interfaces.XECPublicKey) k;
             java.math.BigInteger u = xec.getU();
             byte[] uBytes = u.toByteArray();
             byte[] padded = new byte[32];
             if (uBytes.length > 32) {
                 if (uBytes[0] == 0 && uBytes.length == 33) {
                     System.arraycopy(uBytes, 1, padded, 0, 32);
                 } else {
                     throw new RuntimeException("Key too large");
                 }
             } else {
                 System.arraycopy(uBytes, 0, padded, 32 - uBytes.length, uBytes.length);
             }
             byte[] le = new byte[32];
             for(int i=0; i<32; i++){
                 le[i] = padded[31-i];
             }
             return le;
        }
        throw new IllegalArgumentException("Not XECPublicKey");
    }

    private static byte[] hkdfSha256(byte[] ikm) throws Exception {
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
    }

    @Test
    public void testDecryptX25519() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
        KeyPair bobKp = kpg.generateKeyPair();
        byte[] bobPrivateBytes = getPrivateKeyBytes(bobKp.getPrivate());

        KeyPair aliceKp = kpg.generateKeyPair();

        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(aliceKp.getPrivate());
        ka.doPhase(bobKp.getPublic(), true);
        byte[] sharedSecret = ka.generateSecret();

        byte[] kek = hkdfSha256(sharedSecret);

        byte[] plaintext = "Hello World".getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        SecretKeySpec keySpec = new SecretKeySpec(kek, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] alicePubBytes = getPublicKeyBytes(aliceKp.getPublic());
        byte[] blob = new byte[32 + 12 + ciphertext.length];
        System.arraycopy(alicePubBytes, 0, blob, 0, 32);
        System.arraycopy(iv, 0, blob, 32, 12);
        System.arraycopy(ciphertext, 0, blob, 44, ciphertext.length);

        byte[] decrypted = EncryptionService.decryptX25519(blob, bobPrivateBytes);

        assertArrayEquals(plaintext, decrypted);
    }

}
