package io.figchain.client;

import io.figchain.avro.model.Fig;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * Main class for End-to-End testing purposes.
 * This class is located in src/test/java to avoid being bundled with the library.
 */
public class E2EMain {

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getProperty("baseUrl");
        String envId = System.getProperty("envId");
        String namespace = System.getProperty("namespace");
        String figKey = System.getProperty("figKey");
        String tenantId = System.getProperty("tenantId");
        String encKeyPath = System.getProperty("encKeyPath");
        String authKeyPath = System.getProperty("authKeyPath");
        String authClientId = System.getProperty("authClientId");
        String clientSecret = System.getProperty("clientSecret");

        FigChainClientBuilder builder = new FigChainClientBuilder()
                .withBaseUrl(baseUrl)
                .withEnvironmentId(UUID.fromString(envId))
                .withDefaultContext(new EvaluationContext())
                .withTenantId(tenantId);

        if (namespace != null && !namespace.isEmpty()) {
            builder.withNamespaces(java.util.Set.of(namespace));
        }

        if (authKeyPath != null && !authKeyPath.isEmpty()) {
            builder.withAuthPrivateKeyPath(authKeyPath)
                   .withAuthClientId(authClientId);
        } else {
            builder.withClientSecret(clientSecret);
        }

        if (encKeyPath != null && !encKeyPath.isEmpty()) {
            builder.withEncryptionPrivateKeyPath(encKeyPath);
        }

        try (FigChainClient client = builder.build()) {
            // Start the client fetching
            client.start();

            // Wait for initial fetch to complete
            client.awaitInitialFetch();

            Optional<Fig> fig = client.getFig(figKey);
            if (fig.isPresent()) {
                byte[] payload = fig.get().getPayload().array();

                // Assuming the fig is a simple Avro string for this test
                Schema schema = Schema.create(Schema.Type.STRING);
                DatumReader<Object> reader = new GenericDatumReader<>(schema);
                Decoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
                Object value = reader.read(null, decoder);

                System.out.println("Java Client Decrypted: " + value);
            } else {
                System.err.println("Fig not found for key: " + figKey);
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
