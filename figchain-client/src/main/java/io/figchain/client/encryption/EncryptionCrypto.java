package io.figchain.client.encryption;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import io.figchain.client.util.KeyUtils;
import java.security.NoSuchAlgorithmException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.InvalidAlgorithmParameterException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;

public class EncryptionCrypto {

    private static final String RSA_ALGORITHM = "RSA";
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final String RSA_OAEP_PADDING = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_WRAP = "AESWrap";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    public static PrivateKey loadPrivateKey(Path path) throws IOException {
        return KeyUtils.loadPrivateKey(path);
    }

    public static byte[] decryptRsaOaep(byte[] encryptedBytes, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_OAEP_PADDING);
            OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT
            );
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams);
            return cipher.doFinal(encryptedBytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Failed to decrypt RSA OAEP", e);
        }
    }

    public static byte[] unwrapAesKey(byte[] wrappedKey, byte[] kek) {
        try {
            Cipher cipher = Cipher.getInstance(AES_WRAP);
            SecretKeySpec keySpec = new SecretKeySpec(kek, AES_ALGORITHM);
            cipher.init(Cipher.UNWRAP_MODE, keySpec);
            return cipher.unwrap(wrappedKey, AES_ALGORITHM, Cipher.SECRET_KEY).getEncoded();
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            throw new RuntimeException("Failed to unwrap AES Key", e);
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
