package io.figchain.client.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uses a v3 backup written by a real FigChain server (local dev stack) and its throwaway key.
 */
class BackupV3Test {

    private static final byte[] PRIVATE_KEY =
            HexFormat.of().parseHex("807c5fb05b1ea661625cade3c12c5db6059b95d40e91381233d26d5b1574e440");
    private final ObjectMapper mapper = S3BackupService.backupObjectMapper();

    private BackupFile load() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/backup/backup-v3.json")) {
            return mapper.readValue(in, BackupFile.class);
        }
    }

    @Test
    void decryptsServerWrittenV3Backup() throws IOException {
        BackupPayload payload = S3BackupService.decrypt(load(), PRIVATE_KEY, mapper);
        assertEquals("plugin_e2e", payload.getTenantId());
        assertEquals(2, payload.getItems().size());
    }

    @Test
    void registersSchemasFromPublicSection() throws IOException {
        BackupPayload payload = S3BackupService.decrypt(load(), PRIVATE_KEY, mapper);
        assertTrue(payload.getSchemas().get("tag:figchain.io,2025:shop:address:1").contains("\"Address\""));
    }

    @Test
    void mapsServerLatestFigItemsToFigFamilies() throws IOException {
        String json = """
            {"syncToken":"t","items":[{"definition":{"namespace":"shop","key":"checkout",
              "figId":"982daf00-03e2-5070-b257-01c4a682cbe9","schemaUri":"tag:x:1","schemaVersion":"1",
              "createdAt":"2026-10-30T01:44:45.408Z","updatedAt":"2026-10-30T01:44:45.408Z"},
             "latestFig":{"figId":"982daf00-03e2-5070-b257-01c4a682cbe9","version":"01a18a7b-4734-7bee-a13e-baf6aecb9245",
              "encrypted":false,"encryptionAlgorithm":null,"keyId":null,"wrappedDek":null,
              "payload":{"data":{"binaryData":"BlVTRCg="},
                "schema":{"content":"eyJ0eXBlIjoicmVjb3JkIn0=","uri":"tag:x:1"}}}}]}
            """;
        BackupPayload payload = mapper.readValue(json, BackupPayload.class);
        io.figchain.avro.model.FigFamily family = payload.getItems().get(0);
        assertEquals(1, family.getFigs().size());
        io.figchain.avro.model.Fig fig = family.getFigs().get(0);
        assertEquals(java.util.UUID.fromString("01a18a7b-4734-7bee-a13e-baf6aecb9245"), fig.getVersion());
        assertEquals(fig.getVersion(), family.getDefaultVersion());
        assertArrayEquals(java.util.Base64.getDecoder().decode("BlVTRCg="), toBytes(fig.getPayload()));
        assertFalse(fig.getIsEncrypted());
        assertNotNull(family.getRules());
        assertEquals("{\"type\":\"record\"}", payload.getSchemas().get("tag:x:1"));
    }

    private static byte[] toBytes(java.nio.ByteBuffer b) {
        byte[] out = new byte[b.remaining()];
        b.duplicate().get(out);
        return out;
    }

    @Test
    void tamperedPublicSectionFailsDecryption() throws IOException {
        BackupFile backup = load();
        backup.setPublicData(backup.getPublicData() + " ");
        assertThrows(RuntimeException.class, () -> S3BackupService.decrypt(backup, PRIVATE_KEY, mapper));
    }

    @Test
    void v3RelabelledAsV2FailsDecryption() throws IOException {
        BackupFile backup = load();
        backup.setVersion("v2");
        assertThrows(RuntimeException.class, () -> S3BackupService.decrypt(backup, PRIVATE_KEY, mapper));
    }

    @Test
    void rejectsMissingPublicSectionAndUnknownVersions() throws IOException {
        BackupFile noPublic = load();
        noPublic.setPublicData(null);
        assertThrows(IOException.class, () -> S3BackupService.decrypt(noPublic, PRIVATE_KEY, mapper));

        BackupFile future = load();
        future.setVersion("v4");
        IOException e = assertThrows(IOException.class, () -> S3BackupService.decrypt(future, PRIVATE_KEY, mapper));
        assertTrue(e.getMessage().contains("v4"));
    }

    @Test
    void fingerprintIsSha256OfRawPublicKey() {
        assertEquals("1560c7168e948cebfb07a001592a7cc8be5032230abfd9e056b4e7422299ad5f",
                BackupCrypto.calculateKeyFingerprint(PRIVATE_KEY));
    }
}
