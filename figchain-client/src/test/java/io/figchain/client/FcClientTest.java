package io.figchain.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.figchain.avro.model.FigFamily;
import io.figchain.avro.model.InitialFetchResponse;
import io.figchain.avro.model.UpdateFetchResponse;
import io.figchain.client.polling.PollingStrategy;
import io.figchain.client.store.FigStore;
import io.figchain.client.transport.FcClientTransport;

class FcClientTest {

    @Mock
    FigStore mockFigStore;
    @Mock
    RolloutEvaluator mockRolloutEvaluator;
    @Mock
    FcClientTransport mockFcClientTransport;
    @Mock
    HttpClient mockHttpClient;
    @Mock
    ExecutorService mockFetchExecutor;
    @Mock
    ScheduledExecutorService mockScheduler;
    @Mock
    PollingStrategy mockPollingStrategy;

    private FcClient fcClient;
    private Set<String> testNamespaces;
    private EvaluationContext defaultContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testNamespaces = new HashSet<>();
        testNamespaces.add("test-namespace");
        defaultContext = new EvaluationContext();

        // Configure mockFetchExecutor to execute tasks immediately
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockFetchExecutor).execute(any(Runnable.class));

        doAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            return new CompletableFuture<>().completeAsync(() -> {
                try {
                    return callable.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }).when(mockFetchExecutor).submit(any(Callable.class));

        fcClient = new FcClient(
                mockFigStore,
                mockRolloutEvaluator,
                mockFcClientTransport,
                null, // asOfTimestamp
                testNamespaces,
                mockFetchExecutor,
                3, // maxRetries
                100L, // retryDelayMillis
                java.util.UUID.randomUUID(), // environmentId
                defaultContext
        );
    fcClient.setPollingStrategy(mockPollingStrategy);
    // Release the latch so getFig() does not block in tests
    fcClient._testReleaseInitialFetchLatch();
    }
    @Test
    void testGetFigWithNamespaceAndKeyAndContext() {
    FigFamily dummyFigFamily = FigFamily.newBuilder()
        .setDefinition(io.figchain.avro.model.FigDefinition.newBuilder()
            .setKey("test-key")
            .setNamespace("test-namespace")
            .setFigId(java.util.UUID.randomUUID())
            .setSchemaUri("http://example.com/schema")
            .setSchemaVersion("1.0")
            .setCreatedAt(java.time.Instant.now())
            .setUpdatedAt(java.time.Instant.now())
            .build())
        .setFigs(java.util.Collections.emptyList())
        .build();
    when(mockFigStore.getFigFamily("test-namespace", "test-key")).thenReturn(Optional.of(dummyFigFamily));
    when(mockRolloutEvaluator.evaluate(dummyFigFamily, defaultContext)).thenReturn(Optional.empty());
    Optional<?> result = fcClient.getFig("test-namespace", "test-key", defaultContext);
    assertTrue(result.isEmpty());
    }

    @Test
    void testGetFigWithNamespaceAndKeyUsesDefaultContext() {
    FigFamily dummyFigFamily = FigFamily.newBuilder()
        .setDefinition(io.figchain.avro.model.FigDefinition.newBuilder()
            .setKey("test-key")
            .setNamespace("test-namespace")
            .setFigId(java.util.UUID.randomUUID())
            .setSchemaUri("http://example.com/schema")
            .setSchemaVersion("1.0")
            .setCreatedAt(java.time.Instant.now())
            .setUpdatedAt(java.time.Instant.now())
            .build())
        .setFigs(java.util.Collections.emptyList())
        .build();
    when(mockFigStore.getFigFamily("test-namespace", "test-key")).thenReturn(Optional.of(dummyFigFamily));
    when(mockRolloutEvaluator.evaluate(dummyFigFamily, defaultContext)).thenReturn(Optional.empty());
    Optional<?> result = fcClient.getFig("test-namespace", "test-key");
    assertTrue(result.isEmpty());
    }

    @Test
    void testGetFigWithKeyAndContextSingleNamespace() {
    FigFamily dummyFigFamily = FigFamily.newBuilder()
        .setDefinition(io.figchain.avro.model.FigDefinition.newBuilder()
            .setKey("test-key")
            .setNamespace("test-namespace")
            .setFigId(java.util.UUID.randomUUID())
            .setSchemaUri("http://example.com/schema")
            .setSchemaVersion("1.0")
            .setCreatedAt(java.time.Instant.now())
            .setUpdatedAt(java.time.Instant.now())
            .build())
        .setFigs(java.util.Collections.emptyList())
        .build();
    when(mockFigStore.getFigFamily("test-namespace", "test-key")).thenReturn(Optional.of(dummyFigFamily));
    when(mockRolloutEvaluator.evaluate(dummyFigFamily, defaultContext)).thenReturn(Optional.empty());
    Optional<?> result = fcClient.getFig("test-key", defaultContext);
    assertTrue(result.isEmpty());
    }

    @Test
    void testGetFigWithKeyUsesDefaultContextSingleNamespace() {
    FigFamily dummyFigFamily = FigFamily.newBuilder()
        .setDefinition(io.figchain.avro.model.FigDefinition.newBuilder()
            .setKey("test-key")
            .setNamespace("test-namespace")
            .setFigId(java.util.UUID.randomUUID())
            .setSchemaUri("http://example.com/schema")
            .setSchemaVersion("1.0")
            .setCreatedAt(java.time.Instant.now())
            .setUpdatedAt(java.time.Instant.now())
            .build())
        .setFigs(java.util.Collections.emptyList())
        .build();
    when(mockFigStore.getFigFamily("test-namespace", "test-key")).thenReturn(Optional.of(dummyFigFamily));
    when(mockRolloutEvaluator.evaluate(dummyFigFamily, defaultContext)).thenReturn(Optional.empty());
    Optional<?> result = fcClient.getFig("test-key");
    assertTrue(result.isEmpty());
    }

    @Test
    void testFcClientInitialization() {
        assertNotNull(fcClient);
        assertEquals(mockFigStore, fcClient.getFigStore());
        assertNull(fcClient.getAsOfTimestamp());
        assertEquals(testNamespaces, fcClient.getNamespaces());
    }

    @Test
    void testFcClientThrowsExceptionIfNoNamespaces() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FcClient(mockFigStore, mockRolloutEvaluator, mockFcClientTransport, null, Collections.emptySet(), mockFetchExecutor, 3, 100L, java.util.UUID.randomUUID());
        });
    }

    @Test
    void testStartFetchesInitialDataAndSchedulesUpdates() throws InterruptedException {
        FigFamily dummyFigFamily = FigFamily.newBuilder()
                .setDefinition(io.figchain.avro.model.FigDefinition.newBuilder().setKey("test-key").setNamespace("test-namespace").setFigId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")).setSchemaUri("http://example.com/schema").setSchemaVersion("1.0").setCreatedAt(java.time.Instant.parse("2023-01-01T00:00:00Z")).setUpdatedAt(java.time.Instant.parse("2023-01-01T00:00:00Z")).build())
                .setFigs(
                        java.util.Collections.singletonList(io.figchain.avro.model.Fig.newBuilder()
                                .setFigId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"))
                                .setVersion(java.util.UUID.fromString("00000000-0000-0000-0000-000000000003"))
                                .setPayload(java.nio.ByteBuffer.wrap("payload-1".getBytes())).build()
                        )
                ).build();

        InitialFetchResponse initialResponse = InitialFetchResponse.newBuilder()
                .setCursor("initial-cursor")
                .setFigFamilies(java.util.Collections.emptyList())
                .setEnvironmentId(fcClient.getEnvironmentId())
                .build();

        when(mockFcClientTransport.fetchInitial(eq("test-namespace"), any(java.util.UUID.class), nullable(Instant.class))).thenReturn(initialResponse);

        UpdateFetchResponse updateResponse = UpdateFetchResponse.newBuilder()
                .setCursor("new-cursor")
                .setFigFamilies(java.util.Collections.singletonList(dummyFigFamily))
                .build();
        when(mockFcClientTransport.fetchUpdates(eq("test-namespace"), anyString())).thenReturn(updateResponse);

        fcClient.start();
        verify(mockFcClientTransport, times(1)).fetchInitial(eq("test-namespace"), any(java.util.UUID.class), nullable(Instant.class));

        // Verify that the polling strategy was started
        verify(mockPollingStrategy, times(1)).start();
    }

    @Test
    void testStopShutsDownSchedulerAndGrpcClient() throws Exception {
        // Start the client to ensure polling strategy is running
        when(mockFcClientTransport.fetchInitial(anyString(), any(java.util.UUID.class), nullable(Instant.class)))
            .thenReturn(InitialFetchResponse.newBuilder().setCursor("c").setFigFamilies(Collections.emptyList()).setEnvironmentId(fcClient.getEnvironmentId()).build());

        fcClient.start().get();

        fcClient.stop();

        // Verify polling strategy shutdown
        verify(mockPollingStrategy, times(1)).stop();

        // Verify client transport shutdown
        verify(mockFcClientTransport, times(1)).shutdown();
    }

    @Test
    void testFetchInitialDataWithRetries() throws Exception {
        // Configure mockFcClientTransport to throw an exception twice, then return a successful response
        when(mockFcClientTransport.fetchInitial(eq("test-namespace"), any(java.util.UUID.class), nullable(Instant.class)))
            .thenThrow(new RuntimeException("Simulated network error 1"))
            .thenThrow(new RuntimeException("Simulated network error 2"))
            .thenReturn(InitialFetchResponse.newBuilder()
                    .setCursor("initial-cursor")
                    .setFigFamilies(Collections.emptyList())
                    .setEnvironmentId(fcClient.getEnvironmentId())
            .build());

        fcClient.start().get();

        // Verify that fetchInitial was called maxRetries + 1 times (3 times in this case)
        verify(mockFcClientTransport, times(3)).fetchInitial(eq("test-namespace"), any(java.util.UUID.class), nullable(Instant.class));
    }
}