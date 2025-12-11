package io.figchain.client.bootstrap;

import io.figchain.avro.model.FigFamily;
import io.figchain.client.vault.VaultPayload;
import io.figchain.client.vault.VaultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VaultBootstrapStrategy implements BootstrapStrategy {

    private static final Logger log = LoggerFactory.getLogger(VaultBootstrapStrategy.class);
    private final VaultService vaultService;

    public VaultBootstrapStrategy(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    @Override
    public BootstrapResult bootstrap(Set<String> namespaces) throws Exception {
        log.info("Bootstrapping from FigChain Vault...");
        VaultPayload payload = vaultService.loadBackup();

        List<FigFamily> items = payload.getItems();
        String syncToken = payload.getSyncToken();

        Map<String, String> cursors = new HashMap<>();

        // Populate cursors for all requested namespaces
        // We assume the syncToken from the backup applies to all namespaces at that point in time
        if (syncToken != null) {
            for (String ns : namespaces) {
                cursors.put(ns, syncToken);
            }
        }

        // We could also populate cursors for other namespaces found in items if we wanted
        if (items != null) {
            for (FigFamily family : items) {
                String ns = family.getDefinition().getNamespace().toString();
                if (!cursors.containsKey(ns)) {
                    cursors.put(ns, syncToken); // Use the global sync token
                }
            }
        }

        log.info("Loaded {} items from Vault. SyncToken: {}", items != null ? items.size() : 0, syncToken);

        return new BootstrapResult(items, cursors);
    }
}
