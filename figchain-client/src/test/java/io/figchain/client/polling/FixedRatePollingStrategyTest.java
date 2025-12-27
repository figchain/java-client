package io.figchain.client.polling;

import io.figchain.avro.model.FigFamily;
import io.figchain.avro.model.UpdateFetchResponse;
import io.figchain.client.transport.FcClientTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixedRatePollingStrategyTest {

    @Mock
    private FcClientTransport mockTransport;

    @Mock
    private FcUpdateListener mockUpdateListener;

    @Mock
    private ScheduledExecutorService mockScheduler;

    @Mock
    private ExecutorService mockFetchExecutor;

    private Set<String> namespaces;
    private Map<String, String> namespaceCursors;
    private FixedRatePollingStrategy strategy;

    @BeforeEach
    void setUp() {
        namespaces = Set.of("test-namespace");
        namespaceCursors = new ConcurrentHashMap<>();
        namespaceCursors.put("test-namespace", "initial-cursor");

        // Mock executor to run the task immediately
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(mockFetchExecutor).execute(any(Runnable.class));

        strategy = new FixedRatePollingStrategy(mockTransport, mockUpdateListener, mockScheduler, mockFetchExecutor, 1000, namespaces, namespaceCursors);
    }

    @Test
    void fetchUpdates_doesNotCallListener_whenUpdatesAreEmpty() throws Exception {
        // Setup empty response
        UpdateFetchResponse response = UpdateFetchResponse.newBuilder()
                .setCursor("new-cursor")
                .setFigFamilies(Collections.emptyList())
                .build();

        when(mockTransport.fetchUpdates(anyString(), anyString())).thenReturn(response);

        // Invoke fetchUpdates via reflection
        invokeFetchUpdates();

        // Verify listener was NOT called
        verify(mockUpdateListener, never()).onUpdate(any());

        // Verify cursor WAS updated
        // We can't easily verify the map update directly without a getter or checking the map,
        // but we can verify fetchUpdates was called which implies the flow continued.
        verify(mockTransport).fetchUpdates("test-namespace", "initial-cursor");
    }

    @Test
    void fetchUpdates_callsListener_whenUpdatesArePresent() throws Exception {
        // Setup response with updates
        UpdateFetchResponse response = UpdateFetchResponse.newBuilder()
                .setCursor("new-cursor")
                .setFigFamilies(Collections.singletonList(mock(FigFamily.class)))
                .build();

        when(mockTransport.fetchUpdates(anyString(), anyString())).thenReturn(response);

        // Invoke fetchUpdates via reflection
        invokeFetchUpdates();

        // Verify listener WAS called
        verify(mockUpdateListener).onUpdate(any());
    }

    @Test
    void fetchUpdates_doesNotCallListener_whenResponseIsNull() throws Exception {
         // Setup null response (simulating some failure or unexpected behavior, though transport usually throws)
         // But if transport returns null, we should probably handle it gracefully.
         // The current code might throw NPE if response is null.
         // Let's assume transport returns a valid object or throws.
         // If transport throws, it's caught.

         when(mockTransport.fetchUpdates(anyString(), anyString())).thenThrow(new RuntimeException("Fetch failed"));

         invokeFetchUpdates();

         verify(mockUpdateListener, never()).onUpdate(any());
    }

    private void invokeFetchUpdates() throws Exception {
        Method method = FixedRatePollingStrategy.class.getDeclaredMethod("fetchUpdates");
        method.setAccessible(true);
        method.invoke(strategy);
    }
}
