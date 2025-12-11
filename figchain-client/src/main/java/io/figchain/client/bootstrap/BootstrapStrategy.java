package io.figchain.client.bootstrap;

import java.util.Set;

public interface BootstrapStrategy {

    /**
     * Bootstraps the client with initial data.
     *
     * @param namespaces the namespaces to bootstrap
     * @return the bootstrap result containing data and cursors
     * @throws Exception if bootstrapping fails
     */
    BootstrapResult bootstrap(Set<String> namespaces) throws Exception;
}
