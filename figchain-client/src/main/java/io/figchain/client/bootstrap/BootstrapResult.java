package io.figchain.client.bootstrap;

import io.figchain.avro.model.FigFamily;

import java.util.List;
import java.util.Map;

public class BootstrapResult {

    private final List<FigFamily> figFamilies;
    private final Map<String, String> cursors;

    public BootstrapResult(List<FigFamily> figFamilies, Map<String, String> cursors) {
        this.figFamilies = figFamilies;
        this.cursors = cursors;
    }

    public List<FigFamily> getFigFamilies() {
        return figFamilies;
    }

    public Map<String, String> getCursors() {
        return cursors;
    }
}
