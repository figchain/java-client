package io.figchain.client.vault;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.figchain.avro.model.FigFamily;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultPayload {

    private String tenantId;
    private String generatedAt;
    private String syncToken;
    private List<FigFamily> items;

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
}
