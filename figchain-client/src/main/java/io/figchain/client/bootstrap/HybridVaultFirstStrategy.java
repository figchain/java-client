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
    private final FcClientTransport transport;

    public HybridVaultFirstStrategy(BootstrapStrategy vaultStrategy, FcClientTransport transport) {
        this.vaultStrategy = vaultStrategy;
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

        // 2. Catch up from Server
        log.info("Catching up from server...");
        // Use a set to track namespaces processed to handle potential intersection
        Set<String> processedNamespaces = new HashSet<>();

        for (String namespace : namespaces) {
            String cursor = finalCursors.get(namespace);
            // If we have a cursor, we catch up using fetchUpdates
            // If we don't have a cursor, we might need to fetchInitial?
            // For now assuming we only catch up if we have a cursor from Vault.
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
            } else {
                log.warn("No cursor found for namespace {} in Vault data. Skipping catch-up.", namespace);
            }
            processedNamespaces.add(namespace);
        }

        return new BootstrapResult(allFamilies, finalCursors);
    }
}
