package io.figchain.client.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.figchain.client.config.ClientConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.PrivateKey;

/**
 * Service for handling FigChain Vault (S3 Backup) operations.
 */
public class VaultService {

    private static final Logger log = LoggerFactory.getLogger(VaultService.class);

    private final S3VaultFetcher fetcher;
    private final ClientConfiguration config;
    private final ObjectMapper objectMapper;

    // We keep the loaded key to avoid reloading
    private PrivateKey privateKey;

    public VaultService(ClientConfiguration config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        // Initialize fetcher only if enabled
        if (config.isVaultEnabled()) {
            this.fetcher = new S3VaultFetcher(config);
        } else {
            this.fetcher = null;
        }
    }

    public VaultPayload loadBackup() throws IOException {
        if (!config.isVaultEnabled()) {
            throw new IllegalStateException("Vault is not enabled in configuration");
        }

        if (config.getVaultPrivateKeyPath() == null) {
            throw new IllegalArgumentException("Vault Private Key Path must be configured");
        }

        // 1. Load Private Key
        if (privateKey == null) {
            log.debug("Loading private key from {}", config.getVaultPrivateKeyPath());
            privateKey = VaultCrypto.loadPrivateKey(Path.of(config.getVaultPrivateKeyPath()));
        }

        // 2. Calculate Fingerprint
        String fingerprint = VaultCrypto.calculateKeyFingerprint(privateKey);
        log.debug("Calculated key fingerprint: {}", fingerprint);

        // 3. Fetch Encrypted Backup
        log.debug("Fetching backup from S3...");
        VaultBackup backup;
        try (InputStream is = fetcher.fetchBackup(fingerprint)) {
            backup = objectMapper.readValue(is, VaultBackup.class);
        }

        if (backup == null) {
            throw new IOException("Failed to parse backup file");
        }

        // 4. Decrypt AES Key
        log.debug("Decrypting AES key...");
        byte[] aesKey = VaultCrypto.decryptAesKey(backup.getEncryptedKey(), privateKey);

        // 5. Decrypt Data
        log.debug("Decrypting payload...");
        String jsonPayload = VaultCrypto.decryptData(backup.getEncryptedData(), aesKey);

        // 6. Parse Payload
        log.debug("Parsing payload...");
        return objectMapper.readValue(jsonPayload, VaultPayload.class);
    }
}
