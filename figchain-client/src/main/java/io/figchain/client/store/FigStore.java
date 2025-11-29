package io.figchain.client.store;

import io.figchain.avro.model.FigFamily;

import java.util.Map;
import java.util.Optional;



public interface FigStore {
    /**
     * Retrieves a fig family by namespace and key.
     *
     * @param namespace the namespace
     * @param key the key of the fig family
     * @return an Optional containing the fig family if it exists, or an empty Optional otherwise
     */
    Optional<FigFamily> getFigFamily(String namespace, String key);

    /**
     * Retrieves all fig families in the store.
     *
     * @return a map of namespace to (key to fig family)
     */
    Map<String, Map<String, FigFamily>> getAllFigFamilies();

    /**
     * Clears all fig families from the store.
     */
    void clear();

    /**
     * Adds or updates a fig family in the store.
     *
     * @param figFamily the fig family to add or update
     */
    void put(FigFamily figFamily);
}
