package io.figchain.client.backup;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.figchain.avro.model.Fig;
import io.figchain.avro.model.FigFamily;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BackupPayload {

    private static final ObjectMapper ITEM_MAPPER = S3BackupService.backupObjectMapper();

    private String tenantId;
    private String generatedAt;
    private String syncToken;
    private List<FigFamily> items;
    private final Map<String, String> schemas = new HashMap<>();

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getSyncToken() {
        return syncToken;
    }

    public void setSyncToken(String syncToken) {
        this.syncToken = syncToken;
    }

    public List<FigFamily> getItems() {
        return items;
    }

    public void setItems(List<FigFamily> items) {
        this.items = items;
    }

    /** Schema text keyed by schema URI, taken from the schemas embedded in the backup. */
    @JsonIgnore
    public Map<String, String> getSchemas() {
        return schemas;
    }

    /**
     * The server writes each item as {@code {definition, latestFig}}, where latestFig is its Fig
     * JSON with the payload under {@code payload.data.binaryData}. A FigFamily-shaped item
     * ({@code figs}/{@code rules}/{@code defaultVersion}) is also accepted.
     */
    @JsonSetter("items")
    void setRawItems(List<JsonNode> rawItems) {
        if (rawItems == null) {
            this.items = null;
            return;
        }
        List<FigFamily> families = new ArrayList<>(rawItems.size());
        for (JsonNode raw : rawItems) {
            families.add(toFigFamily(raw));
        }
        this.items = families;
    }

    private FigFamily toFigFamily(JsonNode raw) {
        ObjectNode node = raw.deepCopy();
        JsonNode latest = node.remove("latestFig");
        FigFamily family;
        try {
            family = ITEM_MAPPER.treeToValue(node, FigFamily.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid backup item", e);
        }
        if (family.getFigs() == null) {
            family.setFigs(new ArrayList<>());
        }
        if (family.getRules() == null) {
            family.setRules(new ArrayList<>());
        }
        if (latest != null && !latest.isNull()) {
            Fig fig = toFig(latest);
            List<Fig> figs = new ArrayList<>(family.getFigs());
            figs.add(fig);
            family.setFigs(figs);
            if (family.getDefaultVersion() == null) {
                family.setDefaultVersion(fig.getVersion());
            }
            String fallbackUri = family.getDefinition() != null && family.getDefinition().getSchemaUri() != null
                    ? family.getDefinition().getSchemaUri().toString() : null;
            addSchema(latest.path("payload").path("schema"), fallbackUri);
        }
        return family;
    }

    private static Fig toFig(JsonNode f) {
        String figId = f.path("figId").asText(null);
        JsonNode binary = f.path("payload").path("data").path("binaryData");
        if (!binary.isTextual()) {
            throw new IllegalArgumentException("latestFig " + figId + " has no payload.data.binaryData");
        }
        Fig.Builder fig = Fig.newBuilder()
                .setFigId(UUID.fromString(figId))
                .setVersion(UUID.fromString(f.path("version").asText()))
                .setPayload(ByteBuffer.wrap(Base64.getDecoder().decode(binary.asText())))
                .setIsEncrypted(f.path("encrypted").asBoolean(false))
                .setEncryptionAlgorithm(textOrNull(f, "encryptionAlgorithm"))
                .setKeyId(textOrNull(f, "keyId"));
        String wrappedDek = textOrNull(f, "wrappedDek");
        fig.setWrappedDek(wrappedDek == null || wrappedDek.isEmpty()
                ? null : ByteBuffer.wrap(Base64.getDecoder().decode(wrappedDek)));
        return fig.build();
    }

    private void addSchema(JsonNode schema, String fallbackUri) {
        if (schema == null || !schema.isObject()) {
            return;
        }
        String uri = textOrNull(schema, "uri");
        if (uri == null) {
            uri = fallbackUri;
        }
        String content = textOrNull(schema, "content");
        if (uri == null || content == null || content.isEmpty()) {
            return;
        }
        schemas.put(uri, new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8));
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
