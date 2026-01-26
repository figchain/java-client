package io.figchain.client.encryption;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;
import javax.crypto.KeyAgreement;
import java.security.KeyFactory;
import java.security.PrivateKey;
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

public class EncryptionCrypto {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

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

            // Private Key
            // XECPrivateKeySpec takes scalar as byte[].
            KeySpec privSpec = new XECPrivateKeySpec(new NamedParameterSpec("X25519"), privateKeyBytes);
            PrivateKey privKey = kf.generatePrivate(privSpec);

            // Public Key: XECPublicKeySpec takes BigInteger u (big-endian). Wire format is little-endian.
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

            // Derive KEK via HKDF-SHA256
            byte[] kek = hkdfSha256(sharedSecret);

            // Decrypt AES-GCM (IV + Ciphertext)

            byte[] aesGcmInput = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, aesGcmInput, 0, iv.length);
            System.arraycopy(ciphertext, 0, aesGcmInput, iv.length, ciphertext.length);

            return decryptAesGcm(aesGcmInput, kek);

        } catch (Exception e) {
            throw new RuntimeException("X25519 decryption failed", e);
        }
    }

    private static byte[] hkdfSha256(byte[] ikm) {
        try {
            String algo = "HmacSHA256";
            byte[] salt = new byte[32]; // Default salt is HashLen zeros

            Mac mac = Mac.getInstance(algo);
            SecretKeySpec saltKey = new SecretKeySpec(salt, algo);
            mac.init(saltKey);
            byte[] prk = mac.doFinal(ikm);

            // Expand: OKM = HMAC-Hash(PRK, info | 0x01)
            byte[] info = new byte[0];
            byte[] t = new byte[info.length + 1];
            System.arraycopy(info, 0, t, 0, info.length);
            t[t.length - 1] = (byte) 0x01;

            mac.init(new SecretKeySpec(prk, algo));
            byte[] okm = mac.doFinal(t);

            return okm; // 32 bytes
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
