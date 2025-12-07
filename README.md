# FigChain Java Client

Official Java client library for [FigChain](https://figchain.io) configuration management.

<a href="https://github.com/figchain/java-client/releases">
    <img src="https://img.shields.io/github/v/release/figchain/java-client" alt="GitHub Release" />
</a>

## Features

- **Real-time configuration updates** - Subscribe to configuration changes with long polling or fixed-rate strategies
- **Rule-based rollouts** - Evaluate feature flags and configurations based on user context
- **Type-safe models** - Avro-based serialization for efficient data transfer
- **Flexible storage** - In-memory or custom storage backends
- **Java 21+** - Targets modern Java features

## Installation

### Maven

Add the FigChain repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>figchain-public</id>
        <url>https://maven.repo.figchain.io/releases</url>
    </repository>
</repositories>
```

Add the dependency:

```xml
<dependency>
    <groupId>io.figchain</groupId>
    <artifactId>figchain-client</artifactId>
    <version>1.0.6</version>
</dependency>
```

### Gradle

Add the FigChain repository to your `build.gradle`:

```groovy
repositories {
    maven {
        url = uri("https://maven.repo.figchain.io/releases")
    }
}
```

Add the dependency:

```groovy
implementation 'io.figchain:figchain-client:1.0.6'
```

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

## Documentation

For detailed usage instructions, see the [API documentation](https://docs.figchain.io).

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

- [Documentation](https://docs.figchain.io)
- [Issues](https://github.com/figchain/java-client/issues)
- [Contact](mailto:support@figchain.io)
