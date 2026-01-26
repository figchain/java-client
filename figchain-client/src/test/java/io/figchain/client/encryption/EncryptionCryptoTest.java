package io.figchain.client.encryption;

import org.junit.jupiter.api.Test;
import javax.crypto.KeyAgreement;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;
import java.security.*;
import java.security.spec.*;
import java.math.BigInteger;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EncryptionCryptoTest {

    @Test
    public void testDecryptX25519() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
        KeyPair bobKp = kpg.generateKeyPair();
        byte[] bobPrivateBytes = getPrivateKeyBytes(bobKp.getPrivate());
        byte[] bobPublicBytes = getPublicKeyBytes(bobKp.getPublic());

        KeyPair aliceKp = kpg.generateKeyPair();

        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(aliceKp.getPrivate());
        ka.doPhase(bobKp.getPublic(), true);
        byte[] sharedSecret = ka.generateSecret();

        byte[] kek = hkdfSha256(sharedSecret);

        byte[] plaintext = "Hello World".getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

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

        byte[] decrypted = EncryptionCrypto.decryptX25519(blob, bobPrivateBytes);

        assertArrayEquals(plaintext, decrypted);
    }

    private byte[] getPrivateKeyBytes(PrivateKey k) throws Exception {
        if (k instanceof java.security.interfaces.XECPrivateKey) {
             java.security.interfaces.XECPrivateKey xec = (java.security.interfaces.XECPrivateKey) k;
             return xec.getScalar().orElseThrow();
        }
        throw new IllegalArgumentException("Not XECPrivateKey");
    }

    private byte[] getPublicKeyBytes(PublicKey k) throws Exception {
        if (k instanceof java.security.interfaces.XECPublicKey) {
             java.security.interfaces.XECPublicKey xec = (java.security.interfaces.XECPublicKey) k;
             BigInteger u = xec.getU();
             byte[] uBytes = u.toByteArray();

             byte[] padded = new byte[32];
             if (uBytes.length > 32) {
                 if (uBytes[0] == 0 && uBytes.length == 33) {
                     System.arraycopy(uBytes, 1, padded, 0, 32);
                 } else {
                     throw new RuntimeException("Key too large: " + uBytes.length);
                 }
             } else {
                 System.arraycopy(uBytes, 0, padded, 32 - uBytes.length, uBytes.length);
             }

             // Convert to Little Endian
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
}
