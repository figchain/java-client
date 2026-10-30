package io.figchain.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.avro.Schema;
import org.apache.avro.compiler.specific.SpecificCompiler;
import org.apache.avro.generic.GenericData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves FigChain schemas (including the schemas they reference) and compiles them to Java.
 */
public class AvroModelGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SchemaSource source;
    private final GenericData.StringType stringType;

    public AvroModelGenerator(SchemaSource source, GenericData.StringType stringType) {
        this.source = source;
        this.stringType = stringType;
    }

    /**
     * Generates Java sources for the given schemas and everything they reference.
     *
     * @param roots the schemas to generate
     * @param outputDir where to write the sources
     * @return the schemas that were compiled, referenced ones first
     * @throws IOException if a schema can't be fetched, parsed or compiled
     */
    public List<SchemaRef> generate(Collection<SchemaRef> roots, File outputDir) throws IOException {
        Map<SchemaRef, String> ordered = new LinkedHashMap<>();
        for (SchemaRef root : roots) {
            resolve(root, ordered, new LinkedHashSet<>());
        }

        // Parse in dependency order with one parser so references resolve by full name
        Schema.Parser parser = new Schema.Parser();
        for (Map.Entry<SchemaRef, String> entry : ordered.entrySet()) {
            Schema schema;
            try {
                schema = parser.parse(entry.getValue());
            } catch (RuntimeException e) {
                throw new IOException("Invalid Avro schema " + entry.getKey().uri() + ": " + e.getMessage(), e);
            }
            SpecificCompiler compiler = new SpecificCompiler(schema);
            compiler.setStringType(stringType);
            compiler.compileToDestination(null, outputDir);
        }
        return new ArrayList<>(ordered.keySet());
    }

    private void resolve(SchemaRef ref, Map<SchemaRef, String> ordered, Set<SchemaRef> inProgress) throws IOException {
        if (ordered.containsKey(ref)) {
            return;
        }
        if (!inProgress.add(ref)) {
            throw new IOException("Circular schema reference involving " + ref.uri());
        }
        String content = normalize(source.fetch(ref), ref);
        for (SchemaRef dependency : references(content)) {
            resolve(dependency, ordered, inProgress);
        }
        inProgress.remove(ref);
        ordered.put(ref, content);
    }

    // FigChain schemas list the schema URIs they depend on in a top-level "references" array
    private List<SchemaRef> references(String content) throws IOException {
        List<SchemaRef> refs = new ArrayList<>();
        JsonNode node = objectMapper.readTree(content).get("references");
        if (node != null && node.isArray()) {
            for (JsonNode ref : node) {
                if (ref.isTextual()) {
                    refs.add(SchemaRef.parse(ref.asText()));
                }
            }
        }
        return refs;
    }

    /**
     * Fills in the namespace and name the same way the hosted artifact builder did,
     * so generated class names stay stable.
     */
    String normalize(String content, SchemaRef ref) throws IOException {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (IOException e) {
            throw new IOException("Schema " + ref.uri() + " is not valid JSON", e);
        }
        if (!(root instanceof ObjectNode objectNode)) {
            return content;
        }
        if (!objectNode.has("namespace")) {
            objectNode.put("namespace", ref.defaultJavaPackage());
        }
        String name = objectNode.has("name") ? objectNode.get("name").asText() : null;
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            objectNode.put("name", ref.defaultRecordName());
        }
        return objectMapper.writeValueAsString(objectNode);
    }
}
