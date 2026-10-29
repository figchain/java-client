package io.figchain.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the mojo against stub FigChain API and S3 servers, then compiles and uses the generated classes.
 */
class GenerateMojoTest {

    private static final String ADDRESS_URI = "tag:figchain.io,2025:shop:address:1";
    private static final String CUSTOMER_URI = "tag:figchain.io,2025:shop:customer:2";
    // No namespace: the plugin must fill in the same default package the hosted builder used
    private static final String ADDRESS = """
            {"type":"record","name":"Address","fields":[{"name":"city","type":"string"}]}""";
    private static final String CUSTOMER = """
            {"type":"record","name":"Customer","namespace":"com.acme","references":["%s"],
             "fields":[{"name":"name","type":"string"},{"name":"home","type":"io.figchain.schema.shopaddress.Address"}]}"""
            .formatted(ADDRESS_URI);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> requests = new ConcurrentHashMap<>();
    private HttpServer server;
    private String baseUrl;

    @TempDir
    Path tmp;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/schemas/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.put(path, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            String body = switch (path) {
                case "/api/schemas/shop/address/1" -> schemaJson("address", 1, ADDRESS);
                case "/api/schemas/shop/customer/2" -> schemaJson("customer", 2, CUSTOMER);
                default -> null;
            };
            respond(exchange, body == null ? 404 : 200, body == null ? "{}" : body);
        });
        server.createContext("/backups/", exchange -> {
            requests.put(exchange.getRequestURI().getPath(), "s3");
            String publicData = mapper.writeValueAsString(Map.of(
                    "generatedAt", "2026-10-29T00:00:00Z",
                    "schemas", Map.of(
                            ADDRESS_URI, mapper.readTree(schemaJson("address", 1, ADDRESS)),
                            CUSTOMER_URI, mapper.readTree(schemaJson("customer", 2, CUSTOMER)))));
            String backup = mapper.writeValueAsString(Map.of(
                    "version", "v3", "publicData", publicData,
                    "encryptedKey", "AAAA", "encryptedData", "AAAA", "keyFingerprint", "fp"));
            respond(exchange, 200, backup);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void apiSource_generatesSchemaAndItsReferences() throws Exception {
        GenerateMojo mojo = baseMojo();
        mojo.source = "api";
        mojo.baseUrl = baseUrl + "/api";
        mojo.clientSecret = "s3cret";
        mojo.schemas = List.of("shop/customer/2");

        mojo.execute();

        assertEquals("Bearer s3cret", requests.get("/api/schemas/shop/customer/2"));
        assertTrue(requests.containsKey("/api/schemas/shop/address/1"), "referenced schema should be fetched");
        assertGeneratedCodeWorks(mojo.outputDirectory);
    }

    @Test
    void s3Source_generatesEverythingInBackupWithoutPrivateKey() throws Exception {
        GenerateMojo mojo = baseMojo();
        mojo.source = "s3";
        mojo.s3Bucket = "backups";
        mojo.s3Prefix = "tenant-a";
        mojo.s3KeyFingerprint = "fp";
        mojo.s3Region = "us-east-1";
        mojo.s3Endpoint = baseUrl;
        mojo.s3PathStyleAccess = true;

        mojo.execute();

        assertTrue(requests.containsKey("/backups/tenant-a/fp/backup.json"));
        assertGeneratedCodeWorks(mojo.outputDirectory);
    }

    @Test
    void apiSource_reportsMissingSchemaClearly() {
        GenerateMojo mojo = baseMojo();
        mojo.source = "api";
        mojo.baseUrl = baseUrl + "/api";
        mojo.clientSecret = "s3cret";
        mojo.schemas = List.of("shop/nope/1");

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("tag:figchain.io,2025:shop:nope:1"), e.getMessage());
    }

    @Test
    void s3Source_rejectsV2BackupWithHelpfulMessage() throws Exception {
        S3BackupSchemaSource source = new S3BackupSchemaSource("b", null, "fp", null, null, false);
        IOException e = assertThrows(IOException.class, () -> source.parse("{\"version\":\"v2\",\"encryptedData\":\"x\"}"));
        assertTrue(e.getMessage().contains("v3"), e.getMessage());
    }

    private GenerateMojo baseMojo() {
        GenerateMojo mojo = new GenerateMojo();
        mojo.outputDirectory = tmp.resolve("generated").toFile();
        mojo.stringType = "String";
        return mojo;
    }

    /** Compiles the generated sources and round-trips a Customer through Avro binary encoding. */
    private void assertGeneratedCodeWorks(File generated) throws Exception {
        assertTrue(new File(generated, "io/figchain/schema/shopaddress/Address.java").isFile(), "default package applied");
        assertTrue(new File(generated, "com/acme/Customer.java").isFile());

        List<String> sources;
        try (Stream<Path> files = Files.walk(generated.toPath())) {
            sources = files.filter(p -> p.toString().endsWith(".java")).map(Path::toString).toList();
        }
        Path classes = Files.createDirectories(tmp.resolve("classes"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        String[] args = Stream.concat(
                Stream.of("-d", classes.toString(), "-cp", System.getProperty("java.class.path"), "-nowarn"),
                sources.stream()).toArray(String[]::new);
        assertEquals(0, javac.run(null, null, null, args), "generated sources should compile");

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader())) {
            // Avro resolves specific record classes through the context class loader
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> addressClass = loader.loadClass("io.figchain.schema.shopaddress.Address");
            Class<?> customerClass = loader.loadClass("com.acme.Customer");
            Object address = addressClass.getConstructor(String.class).newInstance("Lisbon");
            Object customer = customerClass.getConstructor(String.class, addressClass).newInstance("Ana", address);

            java.nio.ByteBuffer bytes = (java.nio.ByteBuffer) customerClass.getMethod("toByteBuffer").invoke(customer);
            Object decoded = customerClass.getMethod("fromByteBuffer", java.nio.ByteBuffer.class).invoke(null, bytes);
            assertEquals(customer, decoded);
            Object home = customerClass.getMethod("getHome").invoke(decoded);
            assertEquals("Lisbon", addressClass.getMethod("getCity").invoke(home));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private String schemaJson(String name, int version, String content) throws IOException {
        return mapper.writeValueAsString(Map.of("name", name, "version", version, "format", "AVRO", "contentText", content));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
