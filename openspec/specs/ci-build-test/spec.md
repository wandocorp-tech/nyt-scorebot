# ci-build-test Specification

## Purpose
TBD - created by archiving change add-cicd-pipeline. Update Purpose after archive.
## Requirements
### Requirement: Build job compiles and runs unit tests
The build workflow SHALL compile the multi-module Maven project, run all unit tests (excluding `EndToEndTest`), enforce JaCoCo coverage thresholds (≥80%), and produce the Spring Boot executable JAR artifact.

#### Scenario: Successful build with passing unit tests
- **WHEN** the build workflow is triggered
- **THEN** the project SHALL compile, all unit tests SHALL pass, JaCoCo coverage SHALL meet thresholds, and `nyt-scorebot-app-1.0-SNAPSHOT.jar` SHALL be uploaded as a GitHub Actions artifact

#### Scenario: Build failure
- **WHEN** the Maven build fails (compilation error)
- **THEN** the workflow SHALL fail with a non-zero exit code and the error output SHALL be visible in the job logs

#### Scenario: Unit test failure
- **WHEN** one or more unit tests fail
- **THEN** the build workflow SHALL fail with a non-zero exit code

#### Scenario: Coverage below threshold
- **WHEN** JaCoCo coverage falls below 80% instruction or branch coverage
- **THEN** the build workflow SHALL fail

### Requirement: Build uses Java 17
The build workflow SHALL use Java 17 (temurin distribution) to match the project's configured Java version.

#### Scenario: Java version configuration
- **WHEN** the build job sets up the Java environment
- **THEN** it SHALL use `actions/setup-java` with distribution `temurin` and java-version `17`

### Requirement: Maven dependency caching
The build workflow SHALL cache Maven dependencies to speed up subsequent runs.

#### Scenario: Cache hit on repeated build
- **WHEN** the build workflow runs and `~/.m2/repository` matches the `pom.xml` hash
- **THEN** dependencies SHALL be restored from cache instead of re-downloaded

### Requirement: End-to-end test job
The test workflow SHALL run the `EndToEndTest` class against a live Discord connection and database. It SHALL NOT run unit tests.

#### Scenario: E2E test passes
- **WHEN** the test workflow runs with valid Discord credentials and the E2E test passes
- **THEN** the workflow SHALL complete successfully

#### Scenario: E2E test failure
- **WHEN** the `EndToEndTest` fails
- **THEN** the workflow SHALL fail with a non-zero exit code

#### Scenario: Discord token required
- **WHEN** the test workflow runs
- **THEN** it SHALL use the `DISCORD_TOKEN` secret to authenticate with Discord

### Requirement: Build and test standalone triggering
Both the build and test workflows SHALL be independently triggerable via `workflow_dispatch`.

#### Scenario: Manual build trigger
- **WHEN** a user triggers the build workflow via GitHub Actions UI
- **THEN** the build job SHALL compile, run unit tests, and produce the JAR artifact

#### Scenario: Manual test trigger
- **WHEN** a user triggers the test workflow via GitHub Actions UI
- **THEN** the test job SHALL build the project and run the EndToEndTest

