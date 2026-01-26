package io.figchain.client.transport;

import org.junit.jupiter.api.Test;
import java.security.KeyFactory;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class Ed25519KeyTest {

    @Test
    public void test32ByteSeed() {
        // 32-byte dummy seed
        String seedHex = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
        byte[] seedBytes = HexFormat.of().parseHex(seedHex);

        // Ensure length is 32
        if (seedBytes.length != 32) {
            throw new RuntimeException("Test setup error: seed is not 32 bytes");
        }

        assertDoesNotThrow(() -> {
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            // Standard constructor for EdECPrivateKeySpec uses the byte array as the private value (seed).
            var spec = new EdECPrivateKeySpec(new NamedParameterSpec("Ed25519"), seedBytes);
            var key = kf.generatePrivate(spec);
            assertNotNull(key);
            System.out.println("Successfully generated private key from 32-byte seed");
        });
    }
}
