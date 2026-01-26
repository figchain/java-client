package io.figchain.client.backup;

import io.figchain.client.encryption.EncryptionService;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Cryptographic utilities for FigChain S3 Backup.
 * Supports Curve25519 (X25519) operations.
 */
public class BackupCrypto {

    /**
     * Decrypts the AES secret key using the X25519 private key.
     *
     * @param encryptedKeyBase64 Base64 encoded encrypted AES key (X25519 wrapped)
     * @param privateKeyBytes    X25519 Private Key bytes (32 bytes)
     * @return AES Secret Key bytes
     */
    public static byte[] decryptAesKey(String encryptedKeyBase64, byte[] privateKeyBytes) {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedKeyBase64);
        return EncryptionService.decryptX25519(encryptedBytes, privateKeyBytes);
    }

    /**
     * Decrypts the content using AES-GCM.
     *
     * @param encryptedDataBase64 Base64 encoded encrypted data
     * @param aesKey              AES Secret Key
     * @return Decrypted content as UTF-8 String
     */
    public static String decryptData(String encryptedDataBase64, byte[] aesKey) {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedDataBase64);
        byte[] decryptedBytes = EncryptionService.decryptAesGcm(encryptedBytes, aesKey);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * Calculates the SHA-256 fingerprint of the Public Key associated with the
     * Private Key.
     * Supports X25519.
     *
     * @param privateKeyBytes X25519 Private Key scalar (32 bytes)
     * @return SHA-256 hash of the raw public key bytes (hex string).
     */
    public static String calculateKeyFingerprint(byte[] privateKeyBytes) {
        // Derive Public Key from Private Key (Scalar Multiplication on Curve25519)
        byte[] publicKeyBytes = x25519PublicFromPrivate(privateKeyBytes);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKeyBytes);

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // --- X25519 Scalar Multiplication (RFC 7748) ---
    // Minimal implementation to derive public key.

    private static final BigInteger P = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19));
    private static final BigInteger A24 = BigInteger.valueOf(121665);

    private static byte[] x25519PublicFromPrivate(byte[] privateKey) {
        // Clamp the private key
        byte[] scalar = new byte[32];
        System.arraycopy(privateKey, 0, scalar, 0, 32);
        scalar[0] &= 248;
        scalar[31] &= 127;
        scalar[31] |= 64;

        // Base point u = 9
        byte[] basePoint = new byte[32];
        basePoint[0] = 9;

        return curve25519(scalar, basePoint);
    }

    private static byte[] curve25519(byte[] n, byte[] p) {
        // Montgomery ladder
        BigInteger u = decode(p);
        BigInteger k = decode(n);

        BigInteger x_1 = u;
        BigInteger x_2 = BigInteger.ONE;
        BigInteger z_2 = BigInteger.ZERO;
        BigInteger x_3 = u;
        BigInteger z_3 = BigInteger.ONE;
        BigInteger swap = BigInteger.ZERO;

        for (int t = 254; t >= 0; t--) {
            BigInteger k_t = k.testBit(t) ? BigInteger.ONE : BigInteger.ZERO;
            swap = swap.xor(k_t);

            if (swap.equals(BigInteger.ONE)) {
               BigInteger temp;
               temp = x_2; x_2 = x_3; x_3 = temp;
               temp = z_2; z_2 = z_3; z_3 = temp;
            }
            swap = k_t;

            BigInteger A = x_2.add(z_2).mod(P);
            BigInteger AA = A.multiply(A).mod(P);
            BigInteger B = x_2.subtract(z_2).mod(P);
            BigInteger BB = B.multiply(B).mod(P);
            BigInteger E = AA.subtract(BB).mod(P);

            BigInteger C = x_3.add(z_3).mod(P);
            BigInteger D = x_3.subtract(z_3).mod(P);
            BigInteger DA = D.multiply(A).mod(P);
            BigInteger CB = C.multiply(B).mod(P);

            BigInteger x_3_new = DA.add(CB).mod(P).multiply(DA.add(CB).mod(P)).mod(P);
            BigInteger z_3_new = x_1.multiply(DA.subtract(CB).mod(P).multiply(DA.subtract(CB).mod(P)).mod(P)).mod(P);

            BigInteger x_2_new = AA.multiply(BB).mod(P);
            BigInteger z_2_new = E.multiply(AA.add(A24.multiply(E).mod(P)).mod(P)).mod(P);

            x_2 = x_2_new;
            z_2 = z_2_new;
            x_3 = x_3_new;
            z_3 = z_3_new;
        }

        if (swap.equals(BigInteger.ONE)) {
           BigInteger temp;
           temp = x_2; x_2 = x_3; x_3 = temp;
           temp = z_2; z_2 = z_3; z_3 = temp;
        }

        BigInteger result = x_2.multiply(z_2.modInverse(P)).mod(P);
        return encode(result);
    }

    private static BigInteger decode(byte[] bytes) {
        byte[] copy = new byte[32];
        System.arraycopy(bytes, 0, copy, 0, 32);
        // Reverse for BigInteger (which expects big-endian)
        // Wire format is little-endian
        for (int i = 0; i < 16; i++) {
             byte tmp = copy[i];
             copy[i] = copy[31-i];
             copy[31-i] = tmp;
        }
        // Mask the high bit of the last byte (which is now first byte)
        copy[0] &= 0x7F;
        return new BigInteger(1, copy);
    }

    private static byte[] encode(BigInteger y) {
        byte[] in = y.toByteArray();
        byte[] out = new byte[32];

        // Copy to end of out (padding with zeros if needed)
        int len = in.length;
        if (len > 32) {
             System.arraycopy(in, len - 32, out, 0, 32);
        } else {
             System.arraycopy(in, 0, out, 32 - len, len);
        }

        // Reverse to little-endian
        for (int i = 0; i < 16; i++) {
             byte tmp = out[i];
             out[i] = out[31-i];
             out[31-i] = tmp;
        }
        return out;
    }
}
