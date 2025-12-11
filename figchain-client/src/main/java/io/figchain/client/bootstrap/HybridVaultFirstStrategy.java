package io.figchain.client.bootstrap;

import io.figchain.avro.model.FigFamily;
import io.figchain.avro.model.UpdateFetchResponse;
import io.figchain.client.transport.FcClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HybridVaultFirstStrategy implements BootstrapStrategy {

    private static final Logger log = LoggerFactory.getLogger(HybridVaultFirstStrategy.class);

    private final BootstrapStrategy vaultStrategy;
    private final BootstrapStrategy serverStrategy;
    private final FcClientTransport transport;

    public HybridVaultFirstStrategy(BootstrapStrategy vaultStrategy, BootstrapStrategy serverStrategy, FcClientTransport transport) {
        this.vaultStrategy = vaultStrategy;
        this.serverStrategy = serverStrategy;
        this.transport = transport;
    }

    @Override
    public BootstrapResult bootstrap(Set<String> namespaces) throws Exception {
        // 1. Load from Vault
        BootstrapResult vaultResult = vaultStrategy.bootstrap(namespaces);

        List<FigFamily> allFamilies = new ArrayList<>();
        if (vaultResult.getFigFamilies() != null) {
            allFamilies.addAll(vaultResult.getFigFamilies());
        }

        Map<String, String> finalCursors = new HashMap<>();
        if (vaultResult.getCursors() != null) {
            finalCursors.putAll(vaultResult.getCursors());
        }

        // 2. Identify namespaces missing from Vault (no cursor)
        Set<String> missingNamespaces = new HashSet<>();
        for (String namespace : namespaces) {
            if (!finalCursors.containsKey(namespace)) {
                missingNamespaces.add(namespace);
            }
        }

        // 3. Fetch full initial data for missing namespaces from Server
        if (!missingNamespaces.isEmpty()) {
            log.warn("No cursor found for namespaces {} in Vault data. Performing initial fetch from server.", missingNamespaces);
            try {
                BootstrapResult serverResult = serverStrategy.bootstrap(missingNamespaces);
                if (serverResult.getFigFamilies() != null) {
                    allFamilies.addAll(serverResult.getFigFamilies());
                }
                if (serverResult.getCursors() != null) {
                    finalCursors.putAll(serverResult.getCursors());
                }
            } catch (Exception serverEx) {
                log.error("Initial fetch for new namespaces {} failed.", missingNamespaces, serverEx);
                // Log failure but proceed; partial success is better than total failure. Other namespaces from Vault are still valid.
            }
        }

        // 4. Catch up from Server for namespaces that WERE in Vault
        log.info("Catching up from server...");

        for (String namespace : namespaces) {
            // Only catch up for namespaces that were present in Vault.
            // Namespaces missing from Vault were just fully fetched (latest state), so no catch-up needed.

            boolean wasInVault = vaultResult.getCursors() != null && vaultResult.getCursors().containsKey(namespace);

            if (wasInVault) {
                String cursor = finalCursors.get(namespace);
                if (cursor != null) {
                    try {
                        log.debug("Fetching updates for namespace {} from cursor {}", namespace, cursor);
                        UpdateFetchResponse response = transport.fetchUpdates(namespace, cursor);
                        if (response.getFigFamilies() != null) {
                            allFamilies.addAll(response.getFigFamilies());
                        }
                        if (response.getCursor() != null) {
                            finalCursors.put(namespace, response.getCursor().toString());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to catch up for namespace {}. Proceeding with Vault data.", namespace, e);
                        // We proceed with what we have from Vault
                    }
                }
            }
        }

        return new BootstrapResult(allFamilies, finalCursors);
    }
}
