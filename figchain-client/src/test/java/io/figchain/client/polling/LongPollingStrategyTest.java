package io.figchain.client.polling;

import io.figchain.client.transport.LongPollingFcClientTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LongPollingStrategyTest {

    @Mock
    private LongPollingFcClientTransport mockTransport;

    @Mock
    private FcUpdateListener mockUpdateListener;

    private ExecutorService executorService;
    private Set<String> namespaces;
    private Map<String, String> namespaceCursors;
    private LongPollingStrategy strategy;

    // Test constants matching the implementation
    private static final int UPDATE_FREQUENCY_THRESHOLD = 3;
    private static final long UPDATE_FREQUENCY_WINDOW_MS = 10_000; // 10 seconds
    private static final long THROTTLE_DELAY_MS = 500;

    @BeforeEach
    void setUp() {
        executorService = Executors.newCachedThreadPool();
        namespaces = Set.of("test-namespace");
        namespaceCursors = new ConcurrentHashMap<>();
        namespaceCursors.put("test-namespace", "initial-cursor");

        strategy = new LongPollingStrategy(mockTransport, mockUpdateListener, executorService, namespaces, namespaceCursors);
    }

    @Test
    void shouldThrottle_returnsFalse_whenNoUpdates() throws Exception {
        // Test that no throttling occurs when hasUpdates is false
        boolean result = invokeShouldThrottle("test-namespace", false);
        assertFalse(result, "Should not throttle when there are no updates");
    }

    @Test
    void shouldThrottle_returnsFalse_whenNamespaceNotFound() throws Exception {
        // Test that no throttling occurs for unknown namespace
        boolean result = invokeShouldThrottle("unknown-namespace", true);
        assertFalse(result, "Should not throttle for unknown namespace");
    }

    @Test
    void shouldThrottle_returnsFalse_whenBelowThreshold() throws Exception {
        // Test that no throttling occurs when update count is below threshold

        // First update
        boolean result1 = invokeShouldThrottle("test-namespace", true);
        assertFalse(result1, "Should not throttle after 1 update");

        // Second update
        boolean result2 = invokeShouldThrottle("test-namespace", true);
        assertFalse(result2, "Should not throttle after 2 updates");
    }

    @Test
    void shouldThrottle_returnsTrue_whenAtThreshold() throws Exception {
        // Test that throttling occurs when hitting the threshold

        // Add updates up to threshold
        for (int i = 0; i < UPDATE_FREQUENCY_THRESHOLD - 1; i++) {
            boolean result = invokeShouldThrottle("test-namespace", true);
            assertFalse(result, "Should not throttle before reaching threshold");
        }

        // This should trigger throttling
        boolean result = invokeShouldThrottle("test-namespace", true);
        assertTrue(result, "Should throttle when reaching threshold");
    }

    @Test
    void shouldThrottle_returnsFalse_afterWindowExpires() throws Exception {
        // Test that throttling stops after the time window expires

        Queue<Long> timestamps = getNamespaceTimestamps("test-namespace");
        long oldTime = System.currentTimeMillis() - UPDATE_FREQUENCY_WINDOW_MS - 1000; // 1 second past window

        // Add old timestamps that should be outside the window
        for (int i = 0; i < UPDATE_FREQUENCY_THRESHOLD; i++) {
            timestamps.offer(oldTime);
        }

        // Current update should not trigger throttling since old timestamps are expired
        boolean result = invokeShouldThrottle("test-namespace", true);
        assertFalse(result, "Should not throttle when old timestamps are outside window");

        // Verify old timestamps were removed
        assertEquals(1, timestamps.size(), "Should only have current timestamp after cleanup");
    }

    @Test
    void shouldThrottle_cleansUpExpiredTimestamps() throws Exception {
        // Test that expired timestamps are properly removed

        Queue<Long> timestamps = getNamespaceTimestamps("test-namespace");
        long now = System.currentTimeMillis();
        long oldTime = now - UPDATE_FREQUENCY_WINDOW_MS - 1000; // Outside window
        long recentTime = now - 1000; // Inside window

        // Add mix of old and recent timestamps
        timestamps.offer(oldTime);
        timestamps.offer(oldTime);
        timestamps.offer(recentTime);
        timestamps.offer(recentTime);

        // Should clean up old timestamps and add new one
        invokeShouldThrottle("test-namespace", true);

        // Should have 2 recent + 1 new = 3 timestamps (all within window)
        assertEquals(3, timestamps.size(), "Should clean up expired timestamps");

        // All remaining timestamps should be recent
        for (Long timestamp : timestamps) {
            assertTrue(now - timestamp <= UPDATE_FREQUENCY_WINDOW_MS,
                      "All remaining timestamps should be within window");
        }
    }

    @Test
    void shouldThrottle_handlesMultipleNamespaces() throws Exception {
        // Test that throttling works independently for different namespaces

        // Add another namespace
        Set<String> multiNamespaces = Set.of("namespace1", "namespace2");
        Map<String, String> multiCursors = new ConcurrentHashMap<>();
        multiCursors.put("namespace1", "cursor1");
        multiCursors.put("namespace2", "cursor2");

        LongPollingStrategy multiStrategy = new LongPollingStrategy(
            mockTransport, mockUpdateListener, executorService, multiNamespaces, multiCursors);

        // Add updates to namespace1 up to threshold
        for (int i = 0; i < UPDATE_FREQUENCY_THRESHOLD; i++) {
            boolean result = invokeShouldThrottle(multiStrategy, "namespace1", true);
            if (i < UPDATE_FREQUENCY_THRESHOLD - 1) {
                assertFalse(result, "Should not throttle namespace1 before threshold");
            } else {
                assertTrue(result, "Should throttle namespace1 at threshold");
            }
        }

        // namespace2 should not be affected
        boolean result = invokeShouldThrottle(multiStrategy, "namespace2", true);
        assertFalse(result, "namespace2 should not be throttled independently");
    }

    @Test
    void applyThrottleIfNeeded_appliesDelay_whenThrottling() throws Exception {
        // Test that the throttle delay is actually applied

        // Set up conditions for throttling
        for (int i = 0; i < UPDATE_FREQUENCY_THRESHOLD; i++) {
            invokeShouldThrottle("test-namespace", true);
        }

        // Measure time taken by applyThrottleIfNeeded
        long startTime = System.currentTimeMillis();
        invokeApplyThrottleIfNeeded("test-namespace", true);
        long endTime = System.currentTimeMillis();
        long elapsed = endTime - startTime;

        // Should have taken at least the throttle delay
        assertTrue(elapsed >= THROTTLE_DELAY_MS - 50, // Allow 50ms tolerance
                  "Should apply throttle delay of " + THROTTLE_DELAY_MS + "ms, but took " + elapsed + "ms");
    }

    @Test
    void applyThrottleIfNeeded_noDelay_whenNotThrottling() throws Exception {
        // Test that no delay is applied when not throttling

        long startTime = System.currentTimeMillis();
        invokeApplyThrottleIfNeeded("test-namespace", true); // Only 1 update, below threshold
        long endTime = System.currentTimeMillis();
        long elapsed = endTime - startTime;

        // Should complete quickly without delay
        assertTrue(elapsed < 100, "Should not apply delay when not throttling, but took " + elapsed + "ms");
    }

    @Test
    void applyThrottleIfNeeded_noDelay_whenNoUpdates() throws Exception {
        // Test that no delay is applied when hasUpdates is false

        // Even if we had many previous updates, no delay should occur for empty response
        for (int i = 0; i < UPDATE_FREQUENCY_THRESHOLD; i++) {
            invokeShouldThrottle("test-namespace", true);
        }

        long startTime = System.currentTimeMillis();
        invokeApplyThrottleIfNeeded("test-namespace", false); // No updates
        long endTime = System.currentTimeMillis();
        long elapsed = endTime - startTime;

        // Should complete quickly without delay
        assertTrue(elapsed < 100, "Should not apply delay when hasUpdates is false, but took " + elapsed + "ms");
    }

    // Helper methods to access private methods via reflection

    private boolean invokeShouldThrottle(String namespace, boolean hasUpdates) throws Exception {
        return invokeShouldThrottle(strategy, namespace, hasUpdates);
    }

    private boolean invokeShouldThrottle(LongPollingStrategy strategy, String namespace, boolean hasUpdates) throws Exception {
        Method method = LongPollingStrategy.class.getDeclaredMethod("shouldThrottle", String.class, boolean.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(strategy, namespace, hasUpdates);
    }

    private void invokeApplyThrottleIfNeeded(String namespace, boolean hasUpdates) throws Exception {
        Method method = LongPollingStrategy.class.getDeclaredMethod("applyThrottleIfNeeded", String.class, boolean.class);
        method.setAccessible(true);
        method.invoke(strategy, namespace, hasUpdates);
    }

    @SuppressWarnings("unchecked")
    private Queue<Long> getNamespaceTimestamps(String namespace) throws Exception {
        Field field = LongPollingStrategy.class.getDeclaredField("namespaceUpdateTimestamps");
        field.setAccessible(true);
        Map<String, Queue<Long>> timestamps = (Map<String, Queue<Long>>) field.get(strategy);
        return timestamps.get(namespace);
    }
}
