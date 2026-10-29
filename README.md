# FigChain Java Client

Official Java client library for [FigChain](https://figchain.io) configuration management.

<a href="https://github.com/figchain/java-client/releases">
    <img src="https://img.shields.io/github/v/release/figchain/java-client" alt="GitHub Release" />
</a>

## Features

- **Real-time configuration updates** - Long polling by default; set `useLongPolling` to `false` (or `FIGCHAIN_USE_LONG_POLLING=false`) to poll every `pollingInterval` instead. Long polling falls back to polling automatically if the network keeps cutting long-held requests
- **Rule-based rollouts** - Evaluate feature flags and configurations based on user context
- **Type-safe models** - Avro-based serialization for efficient data transfer
- **Flexible storage** - In-memory or custom storage backends
- **Java 21+** - Targets modern Java features

## Installation

Releases are built from GitHub tags by [JitPack](https://jitpack.io). Use a release tag as the version.

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.figchain.java-client</groupId>
    <artifactId>figchain-client</artifactId>
    <version>${figchain.version}</version>
</dependency>
```

Gradle: add `maven { url = uri("https://jitpack.io") }` to `repositories` and
`implementation "com.github.figchain.java-client:figchain-client:${figchainVersion}"`.

## Quick Start

```java
import io.figchain.client.FcClient;
import io.figchain.client.FcClientBuilder;
import io.figchain.client.EvaluationContext;

// Build the client
FcClient client = new FcClientBuilder()
    .apiKey("your-api-key")
    .environmentId("your-environment-id")
    .namespace("default")
    .build();

// Start the client
client.start();

// Evaluate a configuration (for traffic split support)
EvaluationContext context = EvaluationContext.builder()
    .addAttribute("userId", "user123")
    .addAttribute("plan", "premium")
    .build();

GeneratedConfigModel configValue = client.getEvaluatedFig("your-fig-key", context, GeneratedConfigModel.class);

// Shutdown when done
client.shutdown();
```

## Generating Models

Fig values are Avro records. The `figchain-maven-plugin` module generates Java classes for your
schemas during the build, fetching them from the FigChain API or from your S3 backup:

```xml
<plugin>
    <groupId>com.github.figchain.java-client</groupId>
    <artifactId>figchain-maven-plugin</artifactId>
    <version>${figchain.version}</version>
    <executions>
        <execution>
            <goals><goal>generate</goal></goals>
        </execution>
    </executions>
    <configuration>
        <stringType>String</stringType>
        <schemas>
            <schema>payments/checkout-settings/3</schema>
        </schemas>
    </configuration>
</plugin>
```

Add JitPack under `<pluginRepositories>` too. Set `FIGCHAIN_URL` and `FIGCHAIN_CLIENT_SECRET` in the build environment. See
[Generating Java Models](https://docs.figchain.io/clients/java-models/) for the S3 source and all
options.

## Documentation

For detailed usage instructions, see the [Client Documentation](https://docs.figchain.io/clients/java).

## Requirements

- Java 21 or later
- Maven 3.8+ or Gradle 7+

## Building from Source

```bash
mvn clean install
```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

- [Documentation](https://docs.figchain.io) / [Java Client](https://docs.figchain.io/clients/java)
- [Issues](https://github.com/figchain/java-client/issues)
- [Contact](mailto:support@figchain.io)
