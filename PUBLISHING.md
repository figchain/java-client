# Publishing Setup Guide

## Completed Setup

The `java-client` directory is now ready to be published as a separate public repository.

### Structure
```
java-client/
├── figchain-models/          # Avro models (formerly fc-avro-models)
├── figchain-client/          # Java client (formerly fc-client-java)
├── .github/workflows/        # GitHub Actions for automated releases
├── pom.xml                   # Parent POM
├── LICENSE                   # Apache 2.0
├── README.md                 # Documentation
└── .gitignore               # Maven/Java gitignore
```

### Artifact Names
- `io.figchain:figchain-models:1.0.0` - Avro models
- `io.figchain:figchain-client:1.0.0` - Java client library

### Private Repo Status
- fc-avro-models remains in the private repo
- fc-api continues to use the internal fc-avro-models
- Added GitHub Packages repository to root pom.xml for future consumption

## Next Steps

### 1. Create GitHub Repository
```bash
cd java-client
gh repo create figchain/java-client --public --source=. --remote=origin
```

Or manually:
1. Go to https://github.com/organizations/figchain/repositories/new
2. Name: `java-client`
3. Public repository
4. Don't initialize with README (we have one)

### 2. Initialize Git and Push
```bash
cd java-client
git init
git add .
git commit -m "Initial commit: FigChain Java client library"
git branch -M main
git remote add origin git@github.com:figchain/java-client.git
git push -u origin main
```

### 3. Create GitHub Personal Access Token (PAT)

**For Publishing (write:packages):**
1. Go to https://github.com/settings/tokens
2. Click "Generate new token" → "Generate new token (classic)"
3. Name: "Maven Package Publisher"
4. Expiration: Your choice
5. Scopes: Check `write:packages` (includes read)
6. Generate and save the token securely

**For Consuming (read:packages):**
Users will need their own PAT with `read:packages` scope to download from GitHub Packages.

### 4. Configure GitHub Secrets
In the new `java-client` repository:
1. Go to Settings → Secrets and variables → Actions
2. The workflow uses `GITHUB_TOKEN` which is automatically available

### 5. Create First Release

**Via GitHub Web UI:**
1. Go to https://github.com/figchain/java-client/releases/new
2. Tag: `v1.0.0`
3. Target: `main`
4. Title: `v1.0.0 - Initial Release`
5. Description: Release notes
6. Publish release

**Or via gh CLI:**
```bash
cd java-client
git tag v1.0.0
git push origin v1.0.0
gh release create v1.0.0 --title "v1.0.0 - Initial Release" --notes "Initial public release of FigChain Java client"
```

This will trigger the GitHub Action which will:
- Build the project
- Set version to 1.0.0 (from tag)
- Run tests
- Publish to GitHub Packages
- Attach JARs to the release

### 6. Configure Maven Settings for Publishing

Create or update `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_PAT</password>
    </server>
  </servers>
</settings>
```

Replace `YOUR_GITHUB_USERNAME` and `YOUR_GITHUB_PAT` with your credentials.

### 7. Manual Publishing (if needed)

If you need to publish manually without GitHub Actions:

```bash
cd java-client
mvn versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
mvn clean deploy
```

## Consuming the Published Packages

### For End Users

Add to their `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/figchain/java-client</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>io.figchain</groupId>
    <artifactId>figchain-client</artifactId>
    <version>1.0.0</version>
  </dependency>
</dependencies>
```

Add to their `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>github</id>
    <username>THEIR_GITHUB_USERNAME</username>
    <password>THEIR_GITHUB_PAT_WITH_READ_PACKAGES</password>
  </server>
</servers>
```

## Important Notes

1. **Syncing Changes**: When you update Avro schemas in fc-avro-models, manually copy them to java-client/figchain-models

2. **Version Management**: 
   - Releases are triggered by Git tags (e.g., v1.0.0, v1.1.0)
   - No SNAPSHOT versions in public repo
   - Use semantic versioning

3. **GitHub Packages Limitation**: 
   - Requires authentication even for public packages
   - Users need a GitHub PAT with read:packages scope
   - This is a GitHub limitation, not specific to your setup

4. **Alternative: Maven Central**:
   - If you want truly public packages (no auth required)
   - Consider publishing to Maven Central later
   - Requires Sonatype OSSRH account and more setup

## Testing Locally

The java-client builds successfully:
```bash
cd java-client
./figchain-client/mvnw clean install
```

Build output shows all artifacts created:
- figchain-models-1.0.0.jar + sources + javadoc
- figchain-client-1.0.0.jar + sources + javadoc
