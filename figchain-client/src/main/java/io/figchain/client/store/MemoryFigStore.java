package io.figchain.client.store;

import io.figchain.avro.model.FigFamily;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository for configuration fig families.
 */
public class MemoryFigStore implements FigStore {
    // Map<namespace, Map<key, FigFamily>>
    private final Map<String, Map<String, FigFamily>> figFamilies = new ConcurrentHashMap<>();

    @Override
    public Optional<FigFamily> getFigFamily(String namespace, String key) {
        Map<String, FigFamily> nsMap = figFamilies.get(namespace);
        if (nsMap == null) return Optional.empty();
        return Optional.ofNullable(nsMap.get(key));
    }

    @Override
    public Map<String, Map<String, FigFamily>> getAllFigFamilies() {
        // Return a shallow copy for safety
        return new ConcurrentHashMap<>(figFamilies);
    }

    @Override
    public void clear() {
        figFamilies.clear();
    }

    @Override
    public void put(FigFamily figFamily) {
        String namespace = figFamily.getDefinition().getNamespace().toString();
        String key = figFamily.getDefinition().getKey().toString();
        figFamilies.computeIfAbsent(namespace, ns -> new ConcurrentHashMap<>())
                .put(key, figFamily);
    }
}
