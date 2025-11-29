# FigChain Java Client

Official Java client library for [FigChain](https://figchain.io) configuration management.

## Features

- **Real-time configuration updates** - Subscribe to configuration changes with long polling or fixed-rate strategies
- **Rule-based rollouts** - Evaluate feature flags and configurations based on user context
- **Type-safe models** - Avro-based serialization for efficient data transfer
- **Flexible storage** - In-memory or custom storage backends
- **Java 21+** - Built with modern Java features

## Installation

### Maven

Add the GitHub Packages repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/figchain/java-client</url>
    </repository>
</repositories>
```

Add the dependency:

```xml
<dependency>
    <groupId>io.figchain</groupId>
    <artifactId>figchain-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

Add the GitHub Packages repository to your `build.gradle`:

```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/figchain/java-client")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

Add the dependency:

```groovy
implementation 'io.figchain:figchain-client:1.0.0'
```

## Quick Start

```java
import io.figchain.client.FcClient;
import io.figchain.client.FcClientBuilder;
import io.figchain.client.EvaluationContext;
import io.figchain.client.polling.LongPollingStrategy;

// Build the client
FcClient client = new FcClientBuilder()
    .apiKey("your-api-key")
    .environmentId("your-environment-id")
    .namespace("default")
    .apiBaseUrl("https://api.figchain.io")
    .build();

// Set polling strategy
client.setPollingStrategy(new LongPollingStrategy());

// Start the client
client.start();

// Evaluate a configuration
EvaluationContext context = EvaluationContext.builder()
    .addAttribute("userId", "user123")
    .addAttribute("plan", "premium")
    .build();

String configValue = client.getEvaluatedFig("feature-flag-key", context, String.class);

// Shutdown when done
client.shutdown();
```

## Authentication with GitHub Packages

To download this package from GitHub Packages, you need a GitHub Personal Access Token (PAT) with `read:packages` permission.

1. Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate a new token with `read:packages` scope
3. Configure Maven or Gradle with your credentials (see installation instructions above)

## Documentation

For detailed usage instructions, see the [API documentation](https://github.com/figchain/java-client/wiki).

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

- [Documentation](https://figchain.io/docs)
- [Issues](https://github.com/figchain/java-client/issues)
- [Contact](mailto:support@figchain.io)
