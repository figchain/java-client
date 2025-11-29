package io.figchain.client;

import io.figchain.avro.model.UpdateFetchRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AvroEncodingTest {

    @Test
    void testSerializeUpdateFetchRequestWithUuid() throws IOException {
        UUID environmentId = UUID.randomUUID();

        UpdateFetchRequest request = UpdateFetchRequest.newBuilder()
            .setNamespace("test-namespace")
            .setCursor("test-cursor")
            .setEnvironmentId(environmentId)
            .build();

        // This should not throw an exception anymore
        byte[] serialized = AvroEncoding.serializeWithSchema(request);
        assertNotNull(serialized);

        // Deserialize it back
        UpdateFetchRequest deserialized = AvroEncoding.deserializeWithSchema(serialized, UpdateFetchRequest.class);
        assertEquals(request.getNamespace().toString(), deserialized.getNamespace().toString());
        assertEquals(request.getCursor().toString(), deserialized.getCursor().toString());
        assertEquals(request.getEnvironmentId(), deserialized.getEnvironmentId());
    }
}
