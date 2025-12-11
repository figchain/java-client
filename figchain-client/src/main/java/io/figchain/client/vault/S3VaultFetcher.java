package io.figchain.client.vault;

import io.figchain.client.config.ClientConfiguration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class S3VaultFetcher implements AutoCloseable {

    private final S3Client s3Client;
    private final String bucketName;
    private final String objectPrefix;

    public S3VaultFetcher(ClientConfiguration config) {
        this.bucketName = config.getVaultBucket();
        this.objectPrefix = config.getVaultPrefix();

        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build());

        if (config.getVaultRegion() != null) {
            builder.region(Region.of(config.getVaultRegion()));
        }

        if (config.getVaultEndpoint() != null) {
            builder.endpointOverride(URI.create(config.getVaultEndpoint()));
        }

        if (config.isVaultPathStyleAccess()) {
            builder.forcePathStyle(true);
        }

        this.s3Client = builder.build();
    }

    public InputStream fetchBackup(String keyFingerprint) throws IOException {
        String key = "backup.json";
        if (keyFingerprint != null && !keyFingerprint.isEmpty()) {
            key = keyFingerprint + "/" + key;
        }

        if (objectPrefix != null && !objectPrefix.isEmpty()) {
            if (objectPrefix.endsWith("/")) {
                key = objectPrefix + key;
            } else {
                key = objectPrefix + "/" + key;
            }
        }

        // Remove leading slash if present, S3 keys shouldn't implementation-wise start with / usually
        if (key.startsWith("/")) {
            key = key.substring(1);
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(request);
        } catch (NoSuchKeyException e) {
            throw new IOException("Backup file not found at key: " + key, e);
        } catch (Exception e) {
            throw new IOException("Failed to fetch backup from S3", e);
        }
    }

    @Override
    public void close() {
        if (s3Client != null) {
            s3Client.close();
        }
    }
}
