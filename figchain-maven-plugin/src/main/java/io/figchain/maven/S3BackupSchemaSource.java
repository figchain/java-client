package io.figchain.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads schemas from the unencrypted public section of a v3 FigChain S3 backup.
 * Needs only S3 read access, not the backup private key.
 */
public class S3BackupSchemaSource implements SchemaSource {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String bucket;
    private final String key;
    private final String region;
    private final String endpoint;
    private final boolean pathStyle;
    private Map<SchemaRef, String> schemas;

    public S3BackupSchemaSource(String bucket, String prefix, String keyFingerprint, String region, String endpoint, boolean pathStyle) {
        this.bucket = bucket;
        this.key = backupKey(prefix, keyFingerprint);
        this.region = region;
        this.endpoint = endpoint;
        this.pathStyle = pathStyle;
    }

    // Mirrors the server's layout: {prefix}/{fingerprint}/backup.json
    static String backupKey(String prefix, String keyFingerprint) {
        String key = keyFingerprint + "/backup.json";
        if (prefix != null && !prefix.isEmpty()) {
            key = (prefix.endsWith("/") ? prefix : prefix + "/") + key;
        }
        return key.startsWith("/") ? key.substring(1) : key;
    }

    @Override
    public String fetch(SchemaRef ref) throws IOException {
        String content = fetchAll().get(ref);
        if (content == null) {
            throw new IOException("Schema " + ref.uri() + " is not in the backup s3://" + bucket + "/" + key);
        }
        return content;
    }

    @Override
    public synchronized Map<SchemaRef, String> fetchAll() throws IOException {
        if (schemas == null) {
            schemas = parse(download());
        }
        return schemas;
    }

    private String download() throws IOException {
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build());
        if (region != null) {
            builder.region(Region.of(region));
        }
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint));
        }
        builder.forcePathStyle(pathStyle);
        try (S3Client s3 = builder.build()) {
            return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asUtf8String();
        } catch (SdkException e) {
            throw new IOException("Failed to read backup s3://" + bucket + "/" + key + ": " + e.getMessage(), e);
        }
    }

    Map<SchemaRef, String> parse(String backupJson) throws IOException {
        JsonNode backup = objectMapper.readTree(backupJson);
        JsonNode publicData = backup.get("publicData");
        if (publicData == null || publicData.isNull()) {
            throw new IOException("Backup s3://" + bucket + "/" + key + " is format " + backup.path("version").asText("unknown")
                    + " and has no public schemas section; the server must write v3 backups");
        }
        JsonNode schemaNodes = objectMapper.readTree(publicData.asText()).path("schemas");
        Map<SchemaRef, String> result = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = schemaNodes.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            SchemaRef ref = SchemaRef.parse(entry.getKey());
            result.put(ref, ApiSchemaSource.SchemaContent.of(entry.getValue(), ref));
        }
        return result;
    }
}
