package io.figchain.client.transport;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrivateKeyTokenProviderTest {

    @Test
    public void testGetToken() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        EdECPrivateKey priv = (EdECPrivateKey) kp.getPrivate();
        EdECPublicKey pub = (EdECPublicKey) kp.getPublic();

        byte[] privBytes = priv.getBytes().orElseThrow();
        String privHex = HexFormat.of().formatHex(privBytes);

        PrivateKeyTokenProvider provider = new PrivateKeyTokenProvider(
                privHex,
                "test-client-id",
                "test-tenant-id",
                "https://api.figchain.io",
                "test-client-id"
        );

        String token = provider.getToken();
        assertNotNull(token);

        DecodedJWT jwt = JWT.decode(token);
        assertEquals("test-client-id", jwt.getIssuer());
        assertEquals("test-client-id", jwt.getSubject());
        assertEquals("test-tenant-id", jwt.getClaim("tenant_id").asString());

        Algorithm algo = new Ed25519Algorithm(pub, null);
        JWT.require(algo)
                .withIssuer("test-client-id")
                .build()
                .verify(token);
    }
}
