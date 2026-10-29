package io.figchain.maven;

import io.figchain.client.transport.PrivateKeyTokenProvider;
import io.figchain.client.transport.SharedSecretTokenProvider;
import io.figchain.client.transport.TokenProvider;
import org.apache.avro.generic.GenericData;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Generates Java classes from FigChain schemas and adds them to the build.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class GenerateMojo extends AbstractMojo {

    /** Where schemas come from: {@code api} (the FigChain server) or {@code s3} (a v3 backup). */
    @Parameter(property = "figchain.source", defaultValue = "api")
    String source;

    /**
     * Schemas to generate, as {@code namespace/name/version} or schema URIs. Referenced schemas are
     * included automatically. Required for the {@code api} source; for {@code s3}, omitting it
     * generates every schema in the backup.
     */
    @Parameter
    List<String> schemas;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/figchain", required = true)
    File outputDirectory;

    /** Java type for Avro strings: {@code CharSequence}, {@code String} or {@code Utf8}. */
    @Parameter(property = "figchain.stringType", defaultValue = "CharSequence")
    String stringType;

    @Parameter(property = "figchain.skip", defaultValue = "false")
    boolean skip;

    // --- api source ---

    @Parameter(property = "figchain.baseUrl", defaultValue = "${env.FIGCHAIN_URL}")
    String baseUrl;

    /** Shared-secret client token. Use this or the private key settings. */
    @Parameter(property = "figchain.clientSecret", defaultValue = "${env.FIGCHAIN_CLIENT_SECRET}")
    String clientSecret;

    /** Hex Ed25519 private key of a service account. */
    @Parameter(property = "figchain.privateKey", defaultValue = "${env.FIGCHAIN_PRIVATE_KEY}")
    String privateKey;

    @Parameter(property = "figchain.serviceAccountId", defaultValue = "${env.FIGCHAIN_SERVICE_ACCOUNT_ID}")
    String serviceAccountId;

    @Parameter(property = "figchain.tenantId", defaultValue = "${env.FIGCHAIN_TENANT_ID}")
    String tenantId;

    @Parameter(property = "figchain.namespace", defaultValue = "${env.FIGCHAIN_NAMESPACE}")
    String namespace;

    @Parameter(property = "figchain.keyId", defaultValue = "${env.FIGCHAIN_KEY_ID}")
    String keyId;

    // --- s3 source (credentials come from the default AWS provider chain) ---

    @Parameter(property = "figchain.s3.bucket")
    String s3Bucket;

    @Parameter(property = "figchain.s3.prefix")
    String s3Prefix;

    /** Fingerprint of the tenant's backup key; it's part of the backup's object key. */
    @Parameter(property = "figchain.s3.keyFingerprint")
    String s3KeyFingerprint;

    @Parameter(property = "figchain.s3.region")
    String s3Region;

    @Parameter(property = "figchain.s3.endpoint")
    String s3Endpoint;

    @Parameter(property = "figchain.s3.pathStyleAccess", defaultValue = "false")
    boolean s3PathStyleAccess;

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping FigChain model generation");
            return;
        }
        SchemaSource schemaSource = createSource();
        try {
            Collection<SchemaRef> roots = roots(schemaSource);
            List<SchemaRef> generated = new AvroModelGenerator(schemaSource, parseStringType()).generate(roots, outputDirectory);
            getLog().info("Generated FigChain models for " + generated.size() + " schema(s) from " + source + " into " + outputDirectory);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        if (project != null) {
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        }
    }

    private Collection<SchemaRef> roots(SchemaSource schemaSource) throws IOException, MojoExecutionException {
        if (schemas != null && !schemas.isEmpty()) {
            List<SchemaRef> refs = new ArrayList<>();
            for (String s : schemas) {
                try {
                    refs.add(SchemaRef.parse(s));
                } catch (IllegalArgumentException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
            return refs;
        }
        if ("s3".equals(source)) {
            return schemaSource.fetchAll().keySet();
        }
        throw new MojoExecutionException("<schemas> is required when source is 'api'");
    }

    private SchemaSource createSource() throws MojoExecutionException {
        return switch (source) {
            case "api" -> new ApiSchemaSource(require(baseUrl, "baseUrl (or FIGCHAIN_URL)"), tokenProvider());
            case "s3" -> new S3BackupSchemaSource(require(s3Bucket, "s3Bucket"), s3Prefix,
                    require(s3KeyFingerprint, "s3KeyFingerprint"), s3Region, s3Endpoint, s3PathStyleAccess);
            default -> throw new MojoExecutionException("Unknown source '" + source + "'; use 'api' or 's3'");
        };
    }

    private TokenProvider tokenProvider() throws MojoExecutionException {
        if (notBlank(privateKey)) {
            return new PrivateKeyTokenProvider(privateKey, require(serviceAccountId, "serviceAccountId"),
                    require(tenantId, "tenantId"), require(namespace, "namespace"), keyId);
        }
        if (notBlank(clientSecret)) {
            return new SharedSecretTokenProvider(clientSecret);
        }
        throw new MojoExecutionException("The api source needs credentials: set clientSecret, or privateKey with serviceAccountId, tenantId and namespace");
    }

    private GenericData.StringType parseStringType() throws MojoExecutionException {
        try {
            return GenericData.StringType.valueOf(stringType);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Unknown stringType '" + stringType + "'; use CharSequence, String or Utf8");
        }
    }

    private static String require(String value, String name) throws MojoExecutionException {
        if (!notBlank(value)) {
            throw new MojoExecutionException("Missing required setting: " + name);
        }
        return value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
