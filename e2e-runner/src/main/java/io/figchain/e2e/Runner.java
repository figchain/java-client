package io.figchain.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.figchain.client.FigChainClient;
import io.figchain.client.FigChainClientBuilder;
import io.figchain.client.config.ClientConfiguration.BootstrapMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.avro.specific.SpecificRecordBase;

/**
 * Java runner for the cross-client e2e suite (figchain-clients/e2e/run.py). Configured from FC_*
 * environment variables; prints one {@code E2E-RESULT {json}} line.
 *
 * <p>The fig is read into the class figchain-maven-plugin generated from the tenant's schema. Its
 * package carries the tenant id ({@code io.figchain.<tenant>.<namespace>}), which is new on every
 * run, so the class is looked up by name rather than imported.
 */
public class Runner {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, BootstrapMode> MODES = Map.of(
            "server-first", BootstrapMode.SERVER_FIRST,
            "hybrid", BootstrapMode.S3_BACKUP_FIRST,
            "backup-only", BootstrapMode.S3_BACKUP_ONLY,
            "listen", BootstrapMode.SERVER_FIRST);

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int status = 0;
        try {
            run(mode);
        } catch (Throwable t) {
            emit(Map.of("mode", mode, "ok", false, "error", String.valueOf(t)));
            status = 1;
        }
        System.exit(status);
    }

    private static void run(String mode) throws Exception {
        Map<String, String> env = System.getenv();
        String namespace = env.get("FC_NAMESPACE");
        String key = env.get("FC_FIG_KEY");
        Class<? extends SpecificRecordBase> type = Class.forName(
                "io.figchain." + env.get("FC_TENANT_ID") + "." + namespace + ".CheckoutSettings")
                .asSubclass(SpecificRecordBase.class);

        FigChainClient client = new FigChainClientBuilder()
                .withBaseUrl(env.get("FC_BASE_URL"))
                .withTenantId(env.get("FC_TENANT_ID"))
                .withEnvironmentId(UUID.fromString(env.get("FC_ENVIRONMENT_ID")))
                .withNamespaces(Set.of(namespace))
                .withClientSecret(env.get("FC_CLIENT_SECRET"))
                .withPollingInterval(1000)
                .withMaxRetries(1)
                .withRetryDelayMillis(100)
                .withBootstrapMode(MODES.get(mode))
                .withS3BackupEnabled(true)
                .withS3BackupBucket(env.get("FC_S3_BUCKET"))
                .withS3BackupPrefix(env.get("FC_S3_PREFIX"))
                .withS3BackupRegion(env.get("AWS_REGION"))
                .withS3BackupEndpoint(env.get("FC_S3_ENDPOINT"))
                .withS3BackupPathStyleAccess(true)
                .withEncryptionPrivateKey(env.get("FC_ENCRYPTION_PRIVATE_KEY"))
                .build();
        try {
            CompletableFuture<SpecificRecordBase> changed = new CompletableFuture<>();
            Object[] initial = new Object[1];
            client.registerListener(key, type, v -> {
                if (initial[0] != null && !v.equals(initial[0])) {
                    changed.complete(v);
                }
            });
            client.start().get(60, TimeUnit.SECONDS);
            Optional<? extends SpecificRecordBase> value = client.getFig(key, type);
            if (value.isEmpty()) {
                emit(Map.of("mode", mode, "ok", false, "error", "fig not found"));
                return;
            }
            if (!mode.equals("listen")) {
                emit(describe(mode, value.get()));
                return;
            }
            initial[0] = value.get();
            System.out.println("E2E-READY");
            System.out.flush();
            long timeout = Long.parseLong(env.getOrDefault("FC_LISTEN_TIMEOUT", "60"));
            try {
                emit(describe(mode, changed.get(timeout, TimeUnit.SECONDS)));
            } catch (TimeoutException e) {
                emit(Map.of("mode", mode, "ok", false, "error", "listener saw no change"));
            }
        } finally {
            client.close();
        }
    }

    /** Reads the fields through the generated class's own typed getters. */
    private static Map<String, Object> describe(String mode, SpecificRecordBase record) throws Exception {
        Class<?> type = record.getClass();
        String currency = (String) type.getMethod("getCurrency").invoke(record);
        int maxItems = (Integer) type.getMethod("getMaxItems").invoke(record);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("currency", currency);
        value.put("maxItems", maxItems);
        return Map.of("mode", mode, "ok", true, "value", value, "type", type.getName());
    }

    private static void emit(Map<String, Object> result) throws Exception {
        System.out.println("E2E-RESULT " + JSON.writeValueAsString(result));
        System.out.flush();
    }
}
