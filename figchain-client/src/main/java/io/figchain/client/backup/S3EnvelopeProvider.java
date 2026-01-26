package io.figchain.client.backup;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.figchain.client.dto.NamespaceKey;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.figchain.client.FcApiClientException;
import java.io.UncheckedIOException;

import java.net.URI;
import java.util.Optional;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;

public class S3EnvelopeProvider {

    private final String bucketName;
    private final String objectPrefix;
    private final String clientId;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    public S3EnvelopeProvider(String bucketName, String objectPrefix, String region, String accessKey, String secretKey, String endpoint, boolean pathStyle, String clientId) {
        this.bucketName = bucketName;
        this.objectPrefix = objectPrefix;
        this.clientId = clientId;
        this.objectMapper = new ObjectMapper();

        S3ClientBuilder builder = S3Client.builder();

        if (region != null) {
            builder.region(Region.of(region));
        } else {
             builder.region(Region.US_EAST_1);
        }

        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            builder.credentialsProvider(software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create());
        }

        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        if (pathStyle) {
            builder.forcePathStyle(true);
        }

        this.s3Client = builder.build();
    }

    public Optional<NamespaceKey> getEnvelope(String namespace) {
        try {
            String prefix = objectPrefix != null ? objectPrefix : "";
            if (!prefix.endsWith("/") && !prefix.isEmpty()) {
                prefix += "/";
            }

            // Path: {prefix}devices/{clientId}/namespaces/{namespace}.json
            String key = prefix + "devices/" + clientId + "/namespaces/" + namespace + ".json";

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(request);
            String jsonEntry = objectBytes.asUtf8String();

            // Deserialize into NamespaceKey (assuming compatible JSON structure from S3BackupService)
            // The JSON has { "keyId": "...", "wrappedKey": "...", "algorithm": "..." }
            // NamespaceKey requires keyId, wrappedKey, algorithm.
            NamespaceKey nsKey = objectMapper.readValue(jsonEntry, NamespaceKey.class);
            return Optional.of(nsKey);

        } catch (UncheckedIOException | AwsServiceException | SdkClientException e) {
            return Optional.empty();
        } catch (JsonProcessingException ex) {
            throw new FcApiClientException("Unable to read envelope", ex);
        }
    }
}
