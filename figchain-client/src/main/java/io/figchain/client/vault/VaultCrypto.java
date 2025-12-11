package io.figchain.client.vault;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;

import java.util.Base64;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * Cryptographic utilities for FigChain Vault.
 */
public class VaultCrypto {

    private static final String RSA_ALGORITHM = "RSA";
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final String RSA_OAEP_PADDING = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int GCM_TAG_LENGTH_BITS = 128; // 16 bytes
    private static final int IV_LENGTH_BYTES = 12;

    /**
     * Loads a PrivateKey from a PEM file.
     * Supports PKCS#8 format.
     *
     * @param path path to the PEM file
     * @return PrivateKey
     * @throws IOException if file cannot be read or key cannot be parsed
     */
    public static PrivateKey loadPrivateKey(Path path) throws IOException {
        String keyContent = Files.readString(path, StandardCharsets.UTF_8);
        keyContent = keyContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);

            // Try PKCS#8 first
            try {
                return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            } catch (Exception e) {
                // Fallback or re-throw, simpler to just assume PKCS8 for modern usage.
                // If raw RSA key (PKCS#1) is needed, BouncyCastle is usually required or manual ASN.1 parsing.
                // For now assuming PKCS#8 as standard for Java.
                throw new IOException("Failed to load private key. Ensure it is in PKCS#8 format.", e);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA algorithm not supported", e);
        }
    }

    /**
     * Decrypts the AES secret key using the RSA private key.
     *
     * @param encryptedKeyBase64 Base64 encoded encrypted AES key
     * @param privateKey         RSA Private Key
     * @return AES Secret Key bytes
     */
    public static byte[] decryptAesKey(String encryptedKeyBase64, PrivateKey privateKey) {
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedKeyBase64);
            Cipher cipher = Cipher.getInstance(RSA_OAEP_PADDING);

            // Configure OAEP to match specification: SHA-256, MGF1 + SHA-256
            OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
            );

            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams);
            return cipher.doFinal(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt AES key", e);
        }
    }

    /**
     * Decrypts the content using AES-GCM.
     *
     * @param encryptedDataBase64 Base64 encoded encrypted data
     * @param aesKey              AES Secret Key
     * @return Decrypted content as UTF-8 String
     */
    public static String decryptData(String encryptedDataBase64, byte[] aesKey) {
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedDataBase64);

            if (encryptedBytes.length < IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Encrypted data is too short to contain IV");
            }

            // Extract IV (first 12 bytes)
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(encryptedBytes, 0, iv, 0, IV_LENGTH_BYTES);

            // Extract Ciphertext (remaining bytes)
            int ciphertextLength = encryptedBytes.length - IV_LENGTH_BYTES;
            byte[] ciphertext = new byte[ciphertextLength];
            System.arraycopy(encryptedBytes, IV_LENGTH_BYTES, ciphertext, 0, ciphertextLength);

            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, AES_ALGORITHM);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            byte[] decryptedBytes = cipher.doFinal(ciphertext);

            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt payload", e);
        }
    }

    /**
     * Calculates the SHA-256 fingerprint of the Public Key associated with the
     * Private Key.
     * Note: This assumes we have access to the public key or can derive it.
     * Since we only have PrivateKey here, it's tricky to derive Public Key from
     * generic PrivateKey interface
     * unless it's RSAPrivateCrtKey.
     *
     * @param privateKey RSA Private Key, must be an instance of
     *                   {@link java.security.interfaces.RSAPrivateCrtKey}.
     * @return SHA-256 hash of the public key.
     * @throws IllegalArgumentException if the public key cannot be derived from the
     *                                  provided private key.
     */
    public static String calculateKeyFingerprint(PrivateKey privateKey) {
        // In the backup flow, the client might need to know which folder to look in.
        // Typically the S3 path includes the fingerprint.
        // Unless the user explicitly provides the fingerprint or public key, we might need to derive it.
        // Most RSA private keys are CRT keys which contain the public modulus and exponent.

        if (privateKey instanceof java.security.interfaces.RSAPrivateCrtKey) {
            try {
                java.security.interfaces.RSAPrivateCrtKey crtKey = (java.security.interfaces.RSAPrivateCrtKey) privateKey;
                java.security.spec.RSAPublicKeySpec publicKeySpec = new java.security.spec.RSAPublicKeySpec(
                        crtKey.getModulus(), crtKey.getPublicExponent());

                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                java.security.PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

                byte[] encoded = publicKey.getEncoded(); // SubjectPublicKeyInfo
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(encoded);

                // Convert to hex string
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) hexString.append('0');
                    hexString.append(hex);
                }
                return hexString.toString();
            } catch (Exception e) {
                throw new RuntimeException("Failed to derive public key fingerprint", e);
            }
        }
        throw new IllegalArgumentException("Cannot derive public key from non-CRT RSA private key");
    }
}
