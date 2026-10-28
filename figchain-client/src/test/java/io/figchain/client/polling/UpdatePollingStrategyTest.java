package io.figchain.client.polling;

import io.figchain.avro.model.UpdateFetchResponse;
import io.figchain.client.transport.FcClientTransport;
import io.figchain.client.transport.FcNetworkException;
import io.figchain.client.transport.FcTransportException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdatePollingStrategyTest {

    private static final Duration LP = UpdatePollingStrategy.LONG_POLL_WAIT;

    /** Records each request's wait and answers from a script (null = empty success). */
    static class FakeTransport implements FcClientTransport {
        final List<Duration> waits = new CopyOnWriteArrayList<>();
        final List<RuntimeException> script;
        Runnable onExhausted = () -> { };

        FakeTransport(List<RuntimeException> script) {
            this.script = new ArrayList<>(script); // may hold nulls; only the poller thread reads it
        }

        @Override
        public UpdateFetchResponse fetchUpdates(String namespace, String cursor, Duration wait) {
            waits.add(wait);
            RuntimeException outcome = script.isEmpty() ? null : script.remove(0);
            if (script.isEmpty()) {
                onExhausted.run();
            }
            if (outcome != null) {
                throw outcome;
            }
            return UpdateFetchResponse.newBuilder().setCursor(cursor + "+").setFigFamilies(new ArrayList<>()).build();
        }

        @Override public UpdateFetchResponse fetchUpdates(String namespace, String cursor) { throw new UnsupportedOperationException(); }
        @Override public io.figchain.avro.model.InitialFetchResponse fetchInitial(String n, java.util.UUID e, java.time.Instant a) { throw new UnsupportedOperationException(); }
        @Override public List<io.figchain.client.dto.NamespaceKey> getNamespaceKey(String namespace) { throw new UnsupportedOperationException(); }
        @Override public void uploadPublicKey(io.figchain.client.dto.UserPublicKey key) { throw new UnsupportedOperationException(); }
        @Override public String fetchSchema(String namespace, String name, int version) { throw new UnsupportedOperationException(); }
        @Override public void shutdown() { }
    }

    private static FcNetworkException transit() {
        return new FcNetworkException("timed out", new java.net.http.HttpTimeoutException("request timed out"));
    }

    private static FcTransportException serverError() {
        return new FcTransportException("server error", 500);
    }

    private record Run(List<Duration> waits, Map<String, String> cursors) { }

    private static Run run(boolean longPolling, List<RuntimeException> script) throws Exception {
        FakeTransport transport = new FakeTransport(script);
        Map<String, String> cursors = new ConcurrentHashMap<>(Map.of("ns", "c"));
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        UpdatePollingStrategy strategy = new UpdatePollingStrategy(
                transport, (families, schemas) -> { }, executor, Set.of("ns"), cursors, longPolling, 1);
        transport.onExhausted = strategy::stop;

        strategy.start();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "poller stopped");
        return new Run(transport.waits, cursors);
    }

    private static List<RuntimeException> successes(int n) {
        List<RuntimeException> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(null);
        }
        return list;
    }

    @Test
    void longPollingAsksServerToWaitAndAdvancesCursor() throws Exception {
        Run run = run(true, successes(3));
        assertEquals(List.of(LP, LP, LP), run.waits());
        assertEquals("c+++", run.cursors().get("ns"));
    }

    @Test
    void pollingDoesNotWait() throws Exception {
        assertEquals(List.of(Duration.ZERO, Duration.ZERO, Duration.ZERO), run(false, successes(3)).waits());
    }

    @Test
    void fallsBackToPollingAfterRepeatedTransitFailures() throws Exception {
        List<RuntimeException> script = new ArrayList<>(List.of(transit(), transit(), transit()));
        script.addAll(successes(2));
        assertEquals(List.of(LP, LP, LP, Duration.ZERO, Duration.ZERO), run(true, script).waits());
    }

    @Test
    void serverErrorsDoNotTriggerFallback() throws Exception {
        List<RuntimeException> script = new ArrayList<>(List.of(serverError(), serverError(), serverError(), serverError()));
        script.addAll(successes(1));
        assertEquals(List.of(LP, LP, LP, LP, LP), run(true, script).waits());
    }
}
