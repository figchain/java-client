package io.figchain.client.bootstrap;

import io.figchain.avro.model.FigFamily;

import java.util.List;
import java.util.Map;

public class BootstrapResult {

    private final List<FigFamily> figFamilies;
    private final Map<String, String> cursors;
    private final Map<String, String> schemas;

    public BootstrapResult(List<FigFamily> figFamilies, Map<String, String> cursors) {
        this(figFamilies, cursors, null);
    }

    public BootstrapResult(List<FigFamily> figFamilies, Map<String, String> cursors, Map<String, String> schemas) {
        this.figFamilies = figFamilies;
        this.cursors = cursors;
        this.schemas = schemas;
    }

    public List<FigFamily> getFigFamilies() {
        return figFamilies;
    }

    public Map<String, String> getCursors() {
        return cursors;
    }

    public Map<String, String> getSchemas() {
        return schemas;
    }
}
