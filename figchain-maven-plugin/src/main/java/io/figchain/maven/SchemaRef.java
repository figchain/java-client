package io.figchain.maven;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Identifies one version of a FigChain schema.
 *
 * @param namespace the namespace the schema lives in
 * @param name the schema name
 * @param version the schema version
 */
public record SchemaRef(String namespace, String name, int version) {

    /**
     * Parses either a schema URI ({@code tag:figchain.io,2025:namespace:name:version})
     * or the short form {@code namespace/name/version}.
     *
     * @param value the reference to parse
     * @return the parsed reference
     * @throws IllegalArgumentException if the value is in neither form
     */
    public static SchemaRef parse(String value) {
        String trimmed = value.trim();
        String[] parts;
        if (trimmed.startsWith("tag:")) {
            String[] all = URI.create(trimmed).getSchemeSpecificPart().split(":");
            if (all.length != 4 || !all[0].startsWith("figchain.io")) {
                throw new IllegalArgumentException("Not a FigChain schema URI: " + value);
            }
            parts = new String[] {all[1], all[2], all[3]};
        } else {
            parts = trimmed.split("/");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Expected namespace/name/version or a schema URI, got: " + value);
            }
        }
        try {
            return new SchemaRef(decode(parts[0]), decode(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Schema version is not a number: " + value, e);
        }
    }

    /**
     * @return the canonical schema URI, matching what the FigChain server generates
     */
    public String uri() {
        return "tag:figchain.io,2025:" + encode(namespace) + ":" + encode(name) + ":" + version;
    }

    /**
     * @return the API path for fetching this schema, relative to the API base URL
     */
    public String apiPath() {
        return "schemas/" + encode(namespace) + "/" + encode(name) + "/" + version;
    }

    /**
     * Java package used when the schema doesn't declare an Avro namespace.
     * Matches the package the hosted artifact builder used, so generated classes keep their names.
     *
     * @return the default Java package
     */
    public String defaultJavaPackage() {
        return "io.figchain.schema." + (namespace + "-" + name).toLowerCase().replace("-", "");
    }

    /**
     * @return the default record name, the schema name in PascalCase
     */
    public String defaultRecordName() {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            } else {
                capitalizeNext = true;
            }
        }
        return sb.toString();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
