package io.figchain.client.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class FallbackServerFirstStrategy implements BootstrapStrategy {

    private static final Logger log = LoggerFactory.getLogger(FallbackServerFirstStrategy.class);

    private final BootstrapStrategy serverStrategy;
    private final BootstrapStrategy vaultStrategy;

    public FallbackServerFirstStrategy(BootstrapStrategy serverStrategy, BootstrapStrategy vaultStrategy) {
        this.serverStrategy = serverStrategy;
        this.vaultStrategy = vaultStrategy;
    }

    @Override
    public BootstrapResult bootstrap(Set<String> namespaces) throws Exception {
        try {
            return serverStrategy.bootstrap(namespaces);
        } catch (Exception e) {
            log.warn("Server bootstrap failed. Falling back to Vault.", e);
            try {
                return vaultStrategy.bootstrap(namespaces);
            } catch (Exception ve) {
                log.error("Fallback to Vault also failed.", ve);
                // Throw the original exception or the new one?
                // Probably better to throw the vault exception as it was the last resort
                throw ve;
            }
        }
    }
}
