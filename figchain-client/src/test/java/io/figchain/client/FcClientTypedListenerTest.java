package io.figchain.client;

import io.figchain.avro.model.Fig;
import io.figchain.avro.model.FigDefinition;
import io.figchain.avro.model.FigFamily;
import io.figchain.client.store.FigStore;
import io.figchain.client.transport.FcClientTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class FcClientTypedListenerTest {

    @Mock
    FigStore mockFigStore;
    @Mock
    RolloutEvaluator mockRolloutEvaluator;
    @Mock
    FcClientTransport mockFcClientTransport;
    @Mock
    ExecutorService mockFetchExecutor;

    private FcClient fcClient;
    private Set<String> testNamespaces;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testNamespaces = new HashSet<>();
        testNamespaces.add("test-namespace");

        fcClient = new FcClient(
                mockFigStore,
                mockRolloutEvaluator,
                mockFcClientTransport,
                null,
                testNamespaces,
                mockFetchExecutor,
                3,
                100L,
                UUID.randomUUID()
        );
    }

    @Test
    void testTypedListenerIsInvokedOnUpdate() throws IOException {
        String key = "test-key";
        String value = "hello world";
        TestRecord record = new TestRecord(value);
        byte[] payload = AvroEncoding.serializeBinary(record);

        Fig fig = Fig.newBuilder()
                .setFigId(UUID.randomUUID())
                .setVersion(UUID.randomUUID())
                .setPayload(ByteBuffer.wrap(payload))
                .build();

        FigFamily figFamily = FigFamily.newBuilder()
                .setDefinition(FigDefinition.newBuilder()
                        .setKey(key)
                        .setNamespace("test-namespace")
                        .setFigId(UUID.randomUUID())
                        .setSchemaUri("schema-uri")
                        .setSchemaVersion("1.0")
                        .setCreatedAt(Instant.now())
                        .setUpdatedAt(Instant.now())
                        .build())
                .setFigs(Collections.singletonList(fig))
                .build();

        when(mockRolloutEvaluator.evaluate(any(FigFamily.class), any())).thenReturn(Optional.of(fig));

        AtomicReference<TestRecord> receivedRecord = new AtomicReference<>();
        fcClient.registerListener(key, TestRecord.class, receivedRecord::set);

        fcClient.onUpdate(Collections.singletonList(figFamily));

        assertNotNull(receivedRecord.get());
        assertEquals(value, receivedRecord.get().get(0).toString());
    }
}
