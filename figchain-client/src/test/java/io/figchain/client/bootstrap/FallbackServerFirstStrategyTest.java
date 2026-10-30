package io.figchain.client.bootstrap;

import io.figchain.client.transport.FcAuthenticationException;
import io.figchain.client.transport.FcAuthorizationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FallbackServerFirstStrategyTest {

    private final BootstrapResult backup = new BootstrapResult(List.of(), Map.of("ns", "backup"));
    private final BootstrapStrategy backupStrategy = namespaces -> backup;

    @Test
    void usesBackupWhenServerUnreachable() throws Exception {
        BootstrapStrategy server = namespaces -> { throw new IOException("connection refused"); };

        assertSame(backup, new FallbackServerFirstStrategy(server, backupStrategy).bootstrap(Set.of("ns")));
    }

    @Test
    void failsOnRejectedCredentials() {
        for (Exception rejected : List.of(new FcAuthenticationException("bad secret", ""),
                new FcAuthorizationException("wrong env", ""))) {
            BootstrapStrategy server = namespaces -> { throw new RuntimeException("bootstrap failed", rejected); };

            Exception e = assertThrows(Exception.class,
                    () -> new FallbackServerFirstStrategy(server, backupStrategy).bootstrap(Set.of("ns")));
            assertSame(rejected, e.getCause());
        }
    }
}
