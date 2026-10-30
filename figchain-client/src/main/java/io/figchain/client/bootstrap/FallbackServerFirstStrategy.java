package io.figchain.client.bootstrap;

import io.figchain.client.transport.FcAuthenticationException;
import io.figchain.client.transport.FcAuthorizationException;
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
            // A rejected credential is a configuration error, not an outage. Serving the
            // backup would hide it while every later update fails the same way.
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof FcAuthenticationException || t instanceof FcAuthorizationException) {
                    throw e;
                }
            }
            log.warn("Server bootstrap failed. Falling back to Vault.", e);
            try {
                return vaultStrategy.bootstrap(namespaces);
            } catch (Exception ve) {
                log.error("Fallback to Vault also failed.", ve);
                ve.addSuppressed(e);
                throw ve;
            }
        }
    }
}
