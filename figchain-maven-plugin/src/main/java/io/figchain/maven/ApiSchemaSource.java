package io.figchain.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.figchain.client.transport.TokenProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches schemas from the FigChain API using client credentials.
 */
public class ApiSchemaSource implements SchemaSource {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final URI baseUrl;
    private final TokenProvider tokenProvider;

    public ApiSchemaSource(String baseUrl, TokenProvider tokenProvider) {
        this.baseUrl = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.tokenProvider = tokenProvider;
    }

    @Override
    public String fetch(SchemaRef ref) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve(ref.apiPath()))
                .header("Authorization", "Bearer " + tokenProvider.getToken())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted fetching schema " + ref.uri(), e);
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IOException("FigChain rejected the credentials (HTTP " + response.statusCode()
                    + ") fetching schema " + ref.uri() + "; the client needs the READ_SCHEMAS authority");
        }
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch schema " + ref.uri() + ": HTTP " + response.statusCode() + " " + response.body());
        }
        return SchemaContent.of(objectMapper.readTree(response.body()), ref);
    }

    /**
     * Pulls the schema text out of a FigChain schema JSON document.
     */
    static final class SchemaContent {
        private SchemaContent() {
        }

        static String of(JsonNode schema, SchemaRef ref) throws IOException {
            JsonNode text = schema.get("contentText");
            if (text != null && !text.isNull()) {
                return text.asText();
            }
            JsonNode content = schema.get("content");
            if (content != null && !content.isNull()) {
                return new String(content.binaryValue(), java.nio.charset.StandardCharsets.UTF_8);
            }
            throw new IOException("Schema " + ref.uri() + " has no content");
        }
    }
}
