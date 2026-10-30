package io.figchain.client.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.figchain.client.config.ClientConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Service for handling FigChain Vault (S3 Backup) operations.
 */
public class S3BackupService {

    private static final Logger log = LoggerFactory.getLogger(S3BackupService.class);


    private final ClientConfiguration config;
    private final ObjectMapper objectMapper;

    private final byte[] privateKeyBytes;

    public S3BackupService(ClientConfiguration config, ObjectMapper objectMapper, byte[] privateKeyBytes) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.privateKeyBytes = privateKeyBytes;
    }

    public S3BackupService(ClientConfiguration config, ObjectMapper objectMapper, String privateKeyHex) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.privateKeyBytes = java.util.HexFormat.of().parseHex(privateKeyHex);
    }

    public BackupPayload loadBackup() throws IOException {
        if (!config.isS3BackupEnabled()) {
            throw new IllegalStateException("S3 Backup is not enabled in configuration");
        }

        if (privateKeyBytes == null || privateKeyBytes.length == 0) {
            throw new IllegalStateException("S3 Backup Private Key is not configured");
        }

        // 2. Calculate Fingerprint
        String fingerprint = BackupCrypto.calculateKeyFingerprint(privateKeyBytes);
        log.debug("Calculated key fingerprint: {}", fingerprint);

        // 3. Fetch Encrypted Backup
        log.debug("Fetching backup from S3...");
        BackupFile backup;

        try (S3BackupFetcher fetcher = new S3BackupFetcher(config)) {
             try (InputStream is = fetcher.fetchBackup(fingerprint)) {
                backup = objectMapper.readValue(is, BackupFile.class);
            }
        }

        if (backup == null) {
            throw new IOException("Failed to parse backup file");
        }

        return decrypt(backup, privateKeyBytes, objectMapper);
    }

    /**
     * Mapper able to read backup payloads: they contain {@code java.time} values, and fields
     * newer servers add are ignored rather than failing older clients.
     */
    public static ObjectMapper backupObjectMapper() {
        return new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Decrypts a parsed backup file with the tenant's private key. Accepts format v2 and v3; for
     * v3 the public section is authenticated, so a modified backup fails to decrypt.
     */
    static BackupPayload decrypt(BackupFile backup, byte[] privateKeyBytes, ObjectMapper objectMapper) throws IOException {
        String publicData = switch (backup.getVersion() == null ? "" : backup.getVersion()) {
            case "v2" -> null;
            case "v3" -> {
                if (backup.getPublicData() == null) {
                    throw new IOException("v3 backup is missing its publicData section");
                }
                yield backup.getPublicData();
            }
            default -> throw new IOException("Unsupported backup format version: " + backup.getVersion());
        };

        // 4. Decrypt AES Key
        log.debug("Decrypting AES key...");
        byte[] aesKey = BackupCrypto.decryptAesKey(backup.getEncryptedKey(), privateKeyBytes);

        // 5. Decrypt Data
        log.debug("Decrypting payload...");
        String jsonPayload = BackupCrypto.decryptData(backup.getEncryptedData(), aesKey, publicData);

        // 6. Parse Payload
        log.debug("Parsing payload...");
        BackupPayload payload = objectMapper.readValue(jsonPayload, BackupPayload.class);
        if (publicData != null) {
            // v3 public section carries {schemas: {uri: {content: base64}}}; it is authenticated above
            com.fasterxml.jackson.databind.JsonNode schemas = objectMapper.readTree(publicData).path("schemas");
            schemas.fields().forEachRemaining(e -> {
                String content = e.getValue().path("content").asText(null);
                if (content != null && !content.isEmpty()) {
                    payload.getSchemas().putIfAbsent(e.getKey(), new String(
                            java.util.Base64.getDecoder().decode(content), java.nio.charset.StandardCharsets.UTF_8));
                }
            });
        }
        return payload;
    }
}
