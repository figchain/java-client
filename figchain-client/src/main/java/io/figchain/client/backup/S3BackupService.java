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

        // 4. Decrypt AES Key
        log.debug("Decrypting AES key...");
        byte[] aesKey = BackupCrypto.decryptAesKey(backup.getEncryptedKey(), privateKeyBytes);

        // 5. Decrypt Data
        log.debug("Decrypting payload...");
        String jsonPayload = BackupCrypto.decryptData(backup.getEncryptedData(), aesKey);

        // 6. Parse Payload
        log.debug("Parsing payload...");
        return objectMapper.readValue(jsonPayload, BackupPayload.class);
    }
}
