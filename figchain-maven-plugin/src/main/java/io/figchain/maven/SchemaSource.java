package io.figchain.maven;

import java.io.IOException;
import java.util.Map;

/**
 * Where schema content comes from.
 */
public interface SchemaSource {

    /**
     * Fetches the Avro schema text for one schema version.
     *
     * @param ref the schema to fetch
     * @return the schema's Avro JSON
     * @throws IOException if the schema can't be fetched or doesn't exist
     */
    String fetch(SchemaRef ref) throws IOException;

    /**
     * Lists every schema the source knows about, keyed by schema URI, for builds
     * that don't name schemas explicitly.
     *
     * @return all available schemas
     * @throws IOException if the listing can't be read
     * @throws UnsupportedOperationException if the source can't enumerate schemas
     */
    default Map<SchemaRef, String> fetchAll() throws IOException {
        throw new UnsupportedOperationException("This schema source requires an explicit list of schemas");
    }
}
