package io.figchain.client;

import io.figchain.client.config.ClientConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FigChainClientBuilderTest {

    @Test
    void testFromEnvUsesCustomProvider() {
        Map<String, String> env = new HashMap<>();
        env.put("FIGCHAIN_URL", "https://env.example.com");
        env.put("FIGCHAIN_CLIENT_SECRET", "env-secret");
        env.put("FIGCHAIN_ENVIRONMENT_ID", "123e4567-e89b-12d3-a456-426614174000");
        env.put("FIGCHAIN_NAMESPACES", "ns1, ns2");
        env.put("FIGCHAIN_POLLING_INTERVAL_MS", "5000");
        env.put("FIGCHAIN_MAX_RETRIES", "5");
        env.put("FIGCHAIN_RETRY_DELAY_MS", "2000");
        env.put("FIGCHAIN_AS_OF_TIMESTAMP", "2023-01-01T00:00:00Z");

        // Vault Configs
        env.put("FIGCHAIN_VAULT_ENABLED", "true");
        env.put("FIGCHAIN_VAULT_BUCKET", "env-bucket");
        env.put("FIGCHAIN_VAULT_PREFIX", "env-prefix");
        env.put("FIGCHAIN_VAULT_REGION", "us-west-2");
        env.put("FIGCHAIN_VAULT_ENDPOINT", "https://s3.example.com");
        env.put("FIGCHAIN_VAULT_PATH_STYLE_ACCESS", "true");
        env.put("FIGCHAIN_VAULT_PRIVATE_KEY_PATH", "/path/to/key.pem");
        env.put("FIGCHAIN_BOOTSTRAP_MODE", "VAULT_ONLY");

        Function<String, String> envProvider = env::get;

        FigChainClientBuilder builder = new FigChainClientBuilder().fromEnv(envProvider);

        assertEquals("https://env.example.com", getField(builder, "baseUrl"));
        assertEquals("env-secret", getField(builder, "clientSecret"));
        assertEquals(java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), getField(builder, "environmentId"));
        // Namespaces is Set<String>
        Set<?> namespaces = (Set<?>) getField(builder, "namespaces");
        assertTrue(namespaces.contains("ns1"));
        assertTrue(namespaces.contains("ns2"));
        assertEquals(5000L, getField(builder, "pollingInterval"));

        assertEquals(true, getField(builder, "vaultEnabled"));
        assertEquals("env-bucket", getField(builder, "vaultBucket"));
        assertEquals("env-prefix", getField(builder, "vaultPrefix"));
        assertEquals("us-west-2", getField(builder, "vaultRegion"));
        assertEquals("https://s3.example.com", getField(builder, "vaultEndpoint"));
        assertEquals(true, getField(builder, "vaultPathStyleAccess"));
        assertEquals("/path/to/key.pem", getField(builder, "vaultPrivateKeyPath"));
        assertEquals(ClientConfiguration.BootstrapMode.VAULT_ONLY, getField(builder, "bootstrapMode"));
    }

    private Object getField(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
