package io.figchain.client;

import io.figchain.avro.model.Fig;
import io.figchain.avro.model.FigFamily;
import io.figchain.client.store.FigStore;
import io.figchain.client.test.TestConfig;
import io.figchain.client.transport.FcClientTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcClientAvroTest {

    @Mock
    private FigStore figStore;
    @Mock
    private RolloutEvaluator rolloutEvaluator;
    @Mock
    private FcClientTransport fcClientTransport;

    private FcClient fcClient;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @BeforeEach
    void setUp() {
        fcClient = new FcClient(
                figStore,
                rolloutEvaluator,
                fcClientTransport,
                null,
                Set.of("test-namespace"),
                executorService,
                3,
                100,
                UUID.randomUUID(),
                new EvaluationContext(Collections.emptyMap()));
        // Bypass initial fetch for this test
        fcClient._testReleaseInitialFetchLatch();
    }

    @Test
    void testGetFigWithAvroClass() throws IOException {
        String key = "test-key";
        TestConfig testConfig = TestConfig.newBuilder()
                .setSomeString("hello")
                .setSomeInt(42)
                .build();

        byte[] payload = AvroEncoding.serializeBinary(testConfig);
        Fig fig = Fig.newBuilder()
                .setFigId(UUID.randomUUID())
                .setVersion(UUID.randomUUID())
                .setPayload(ByteBuffer.wrap(payload))
                .build();

        when(figStore.getFigFamily(any(), eq(key))).thenReturn(Optional.of(mock(FigFamily.class)));
        when(rolloutEvaluator.evaluate(any(), any())).thenReturn(Optional.of(fig));

        Optional<TestConfig> result = fcClient.getFig(key, TestConfig.class);

        assertTrue(result.isPresent());
        assertEquals("hello", result.get().getSomeString());
        assertEquals(42, result.get().getSomeInt());
    }
}
