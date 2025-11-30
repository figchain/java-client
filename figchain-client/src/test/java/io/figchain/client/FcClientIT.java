package io.figchain.client;

import io.figchain.avro.model.Fig;
import io.figchain.avro.model.FigFamily;
import io.figchain.client.store.MemoryFigStore;
import io.figchain.client.transport.Transport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for FcClient using the Avro endpoints.
 * This test requires the server to be running on localhost:8080.
 */
public class FcClientIT {

    private static final Logger log = LoggerFactory.getLogger(FcClientIT.class);

    private String getClientSecret() {
        return System.getenv().getOrDefault("FIGCHAIN_CLIENT_SECRET", "dummy-secret");
    }

    @Test
    public void testPollingTransport() throws Exception {
        FcClientBuilder builder = new FcClientBuilder();
        builder.withBaseUrl("http://localhost:8080/api");
        builder.withClientSecret(getClientSecret());
        builder.withNamespaces(Collections.singleton("namespace1"));
        builder.withEnvironmentId(java.util.UUID.fromString("0199738e-78ad-7194-9f50-7afa15cc8de9")); // Test environment ID
        builder.withFigStore(new MemoryFigStore());

        // Use regular polling transport
        builder.withTransport(Transport.POLLING);
        builder.withPollingInterval(5000);

        CountDownLatch latch = new CountDownLatch(1);

        try {
            builder.withUpdateListener((List<FigFamily> figFamilies) -> {
                log.debug("Received update with {} figFamilies", figFamilies.size());
                for (FigFamily family : figFamilies) {
                    log.debug("FigFamily: {} with {} figs", family, family.getFigs().size());
                    for (Fig fig : family.getFigs()) {
                        log.debug("  Fig: {} = {}", fig.getFigId(), fig.getPayload().remaining() + " bytes");
                    }
                }
                latch.countDown();
            });

            FcClient client = builder.build();

            log.info("Starting client...");
            // Block until the initial fetch is complete to avoid race conditions in the test.
            client.start().get(30, TimeUnit.SECONDS);

            // Wait up to 30 seconds for initial data
            boolean received = latch.await(30, TimeUnit.SECONDS);

            assertTrue(received, "Should have received initial fig data within 30 seconds");

            // Verify we got some data
            Map<String, Map<String, FigFamily>> allFamilies = client.getFigStore().getAllFigFamilies();
            int totalFamilies = allFamilies.values().stream().mapToInt(Map::size).sum();
            log.info("Retrieved {} fig families from client store", totalFamilies);

            assertNotNull(allFamilies, "Fig families should not be null");
            assertTrue(totalFamilies > 0, "Should have received at least one fig family");

            client.stop();
            log.info("Polling transport test completed successfully");

        } catch (Exception e) {
            log.error("Error in polling transport test", e);
            throw e;
        }
    }

    @Test
    public void testLongPollingTransport() throws Exception {
        log.info("Starting long polling transport integration test");

        // Build the client with same configuration as your demo app
        FcClientBuilder builder = new FcClientBuilder();
        builder.withBaseUrl("http://localhost:8080/api");
        builder.withClientSecret(getClientSecret());
        builder.withNamespaces(Collections.singleton("namespace1"));
        builder.withEnvironmentId(java.util.UUID.fromString("0199738e-78ad-7194-9f50-7afa15cc8de9")); // Test environment ID
        builder.withFigStore(new MemoryFigStore());

        // Use long polling transport
        builder.withTransport(Transport.LONG_POLLING);
        // Note: Long polling timeout is set at transport level, not builder level

        CountDownLatch latch = new CountDownLatch(1);

        try {
            builder.withUpdateListener((List<FigFamily> figFamilies) -> {
                log.debug("Received update with {} figFamilies", figFamilies.size());
                for (FigFamily family : figFamilies) {
                    log.debug("FigFamily: {} with {} figs", family, family.getFigs().size());
                    for (Fig fig : family.getFigs()) {
                        log.debug("  Fig: {} = {}", fig.getFigId(), fig.getPayload().remaining() + " bytes");
                    }
                }
                latch.countDown();
            });

            FcClient client = builder.build();

            log.info("Starting client...");
            // Block until the initial fetch is complete to avoid race conditions in the test.
            client.start().get(45, TimeUnit.SECONDS);

            // Wait up to 45 seconds for initial data (longer for long polling)
            boolean received = latch.await(45, TimeUnit.SECONDS);

            assertTrue(received, "Should have received initial fig data within 45 seconds");

            // Verify we got some data
            Map<String, Map<String, FigFamily>> allFamilies = client.getFigStore().getAllFigFamilies();
            int totalFamilies = allFamilies.values().stream().mapToInt(Map::size).sum();
            log.info("Retrieved {} fig families from client store", totalFamilies);

            assertNotNull(allFamilies, "Fig families should not be null");
            assertTrue(totalFamilies > 0, "Should have received at least one fig family");

            client.stop();
            log.info("Long polling transport test completed successfully");

        } catch (Exception e) {
            log.error("Error in long polling transport test", e);
            throw e;
        }
    }
}