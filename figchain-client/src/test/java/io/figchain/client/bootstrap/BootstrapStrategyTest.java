package io.figchain.client.bootstrap;


import io.figchain.avro.model.InitialFetchResponse;
import io.figchain.avro.model.UpdateFetchResponse;
import io.figchain.client.transport.FcClientTransport;
import io.figchain.client.vault.VaultPayload;
import io.figchain.client.vault.VaultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class BootstrapStrategyTest {

    @Mock
    private FcClientTransport transport;

    @Mock
    private VaultService vaultService;

    private final UUID environmentId = UUID.randomUUID();
    private final Set<String> namespaces = Collections.singleton("test-ns");

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testServerBootstrapSuccess() throws Exception {
        InitialFetchResponse response = InitialFetchResponse.newBuilder()
                .setCursor("server-cursor")
                .setFigFamilies(Collections.emptyList())
                .setEnvironmentId(environmentId)
                .build();

        when(transport.fetchInitial(eq("test-ns"), eq(environmentId), any())).thenReturn(response);

        ServerBootstrapStrategy strategy = new ServerBootstrapStrategy(transport, environmentId, null, 1, 10);
        BootstrapResult result = strategy.bootstrap(namespaces);

        assertNotNull(result);
        assertEquals("server-cursor", result.getCursors().get("test-ns"));
        verify(transport, times(1)).fetchInitial(eq("test-ns"), eq(environmentId), any());
    }

    @Test
    public void testVaultBootstrapSuccess() throws Exception {
        VaultPayload payload = new VaultPayload();
        payload.setSyncToken("vault-sync-token");
        payload.setItems(Collections.emptyList());

        when(vaultService.loadBackup()).thenReturn(payload);

        VaultBootstrapStrategy strategy = new VaultBootstrapStrategy(vaultService);
        BootstrapResult result = strategy.bootstrap(namespaces);

        assertNotNull(result);
        assertEquals("vault-sync-token", result.getCursors().get("test-ns"));
        verify(vaultService, times(1)).loadBackup();
    }

    @Test
    public void testHybridStrategySuccess() throws Exception {
        // Vault setup
        VaultPayload payload = new VaultPayload();
        payload.setSyncToken("vault-cursor");
        payload.setItems(Collections.emptyList());
        when(vaultService.loadBackup()).thenReturn(payload);
        VaultBootstrapStrategy vaultStrategy = new VaultBootstrapStrategy(vaultService);

        // Server update setup
        UpdateFetchResponse updateResponse = UpdateFetchResponse.newBuilder()
                .setCursor("server-latest-cursor")
                .setFigFamilies(Collections.emptyList())
                .build();
        when(transport.fetchUpdates(eq("test-ns"), eq("vault-cursor"))).thenReturn(updateResponse);

        HybridVaultFirstStrategy strategy = new HybridVaultFirstStrategy(vaultStrategy, transport);
        BootstrapResult result = strategy.bootstrap(namespaces);

        assertNotNull(result);
        assertEquals("server-latest-cursor", result.getCursors().get("test-ns"));
        verify(vaultService, times(1)).loadBackup();
        verify(transport, times(1)).fetchUpdates(eq("test-ns"), eq("vault-cursor"));
    }

    @Test
    public void testFallbackStrategy_ServerFails_VaultSucceeds() throws Exception {
        // Server fails
        when(transport.fetchInitial(anyString(), any(), any())).thenThrow(new RuntimeException("Server down"));

        // Vault setup
        VaultPayload payload = new VaultPayload();
        payload.setSyncToken("vault-cursor");
        payload.setItems(Collections.emptyList());
        when(vaultService.loadBackup()).thenReturn(payload);

        ServerBootstrapStrategy serverStrategy = new ServerBootstrapStrategy(transport, environmentId, null, 0, 0);
        VaultBootstrapStrategy vaultStrategy = new VaultBootstrapStrategy(vaultService);

        FallbackServerFirstStrategy strategy = new FallbackServerFirstStrategy(serverStrategy, vaultStrategy);
        BootstrapResult result = strategy.bootstrap(namespaces);

        assertNotNull(result);
        assertEquals("vault-cursor", result.getCursors().get("test-ns"));
        verify(vaultService, times(1)).loadBackup();
    }
}
