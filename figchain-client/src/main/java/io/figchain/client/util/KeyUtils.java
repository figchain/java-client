package io.figchain.client.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Utility class for cryptographic key operations.
 */
public class KeyUtils {

    /**
     * Loads an RSA private key from a PEM file.
     * Supports both PKCS#8 and RSA-specific PEM formats.
     *
     * @param path the path to the private key file
     * @return the loaded private key
     * @throws IOException if the file cannot be read or the key is invalid
     */
    public static RSAPrivateKey loadPrivateKey(Path path) throws IOException {
        String keyContent = Files.readString(path, StandardCharsets.UTF_8);
        return parsePrivateKey(keyContent);
    }

    /**
     * Parses an RSA private key from a PEM string.
     *
     * @param keyContent the PEM string
     * @return the parsed private key
     * @throws IOException if the key is invalid
     */
    public static RSAPrivateKey parsePrivateKey(String keyContent) throws IOException {
        String privateKeyPEM = keyContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        try {
            byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new IOException("Failed to parse private key", e);
        }
    }
}
