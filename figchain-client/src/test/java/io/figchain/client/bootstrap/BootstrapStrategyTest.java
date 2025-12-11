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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

        ServerBootstrapStrategy serverStrategy = mock(ServerBootstrapStrategy.class);

        HybridVaultFirstStrategy strategy = new HybridVaultFirstStrategy(vaultStrategy, serverStrategy, transport);
        BootstrapResult result = strategy.bootstrap(namespaces);

        assertNotNull(result);
        assertEquals("server-latest-cursor", result.getCursors().get("test-ns"));
        verify(vaultService, times(1)).loadBackup();
        verify(transport, times(1)).fetchUpdates(eq("test-ns"), eq("vault-cursor"));
        verify(serverStrategy, never()).bootstrap(anySet());
    }

    @Test
    public void testHybridStrategy_MissingInVault_FetchesFromServer() throws Exception {
        Set<String> twoNamespaces = Set.of("ns-present", "ns-missing");

        // Vault setup - only has ns-present
        VaultPayload payload = new VaultPayload();
        payload.setSyncToken("vault-cursor");
        // Vault Strategy Mock: Only returns data for 'ns-present', simulating 'ns-missing' was not in Vault.

        BootstrapStrategy mockVaultStrategy = mock(BootstrapStrategy.class);
        Map<String, String> vaultCursors = new HashMap<>();
        vaultCursors.put("ns-present", "vault-cursor");
        BootstrapResult vaultResult = new BootstrapResult(new ArrayList<>(), vaultCursors);
        when(mockVaultStrategy.bootstrap(twoNamespaces)).thenReturn(vaultResult);

        // Server Strategy - used for missing namespace
        BootstrapStrategy mockServerStrategy = mock(BootstrapStrategy.class);
        Map<String, String> serverCursors = new HashMap<>();
        serverCursors.put("ns-missing", "server-initial-cursor");
        BootstrapResult serverResult = new BootstrapResult(new ArrayList<>(), serverCursors);
        when(mockServerStrategy.bootstrap(Collections.singleton("ns-missing"))).thenReturn(serverResult);

        // Transport - used for catching up ns-present
        UpdateFetchResponse updateResponse = UpdateFetchResponse.newBuilder()
                .setCursor("server-latest-cursor")
                .setFigFamilies(Collections.emptyList())
                .build();
        when(transport.fetchUpdates(eq("ns-present"), eq("vault-cursor"))).thenReturn(updateResponse);

        HybridVaultFirstStrategy strategy = new HybridVaultFirstStrategy(mockVaultStrategy, mockServerStrategy, transport);
        BootstrapResult result = strategy.bootstrap(twoNamespaces);

        assertNotNull(result);
        assertEquals("server-latest-cursor", result.getCursors().get("ns-present"));
        assertEquals("server-initial-cursor", result.getCursors().get("ns-missing"));

        verify(mockVaultStrategy, times(1)).bootstrap(twoNamespaces);
        verify(mockServerStrategy, times(1)).bootstrap(Collections.singleton("ns-missing"));
        verify(transport, times(1)).fetchUpdates(eq("ns-present"), eq("vault-cursor"));
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
