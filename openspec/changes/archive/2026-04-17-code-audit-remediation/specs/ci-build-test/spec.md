## MODIFIED Requirements

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

### Requirement: Pipeline triggers scoped to main branch and pull requests
The CI/CD pipeline SHALL trigger only on pushes to the `main` branch and on pull request events targeting `main`. Pushes to feature branches SHALL NOT trigger the full pipeline.

#### Scenario: Push to main triggers pipeline
- **WHEN** a commit is pushed to the `main` branch
- **THEN** the full pipeline (build, test, deploy, release) runs

#### Scenario: Pull request triggers build and test
- **WHEN** a pull request targeting `main` is opened or updated
- **THEN** the build and test jobs run (but not deploy or release)

#### Scenario: Push to feature branch does not trigger pipeline
- **WHEN** a commit is pushed to a branch other than `main`
- **THEN** no pipeline workflow runs

### Requirement: Version string centralized via Maven property
The JAR artifact name SHALL be derived from `${project.version}` in all CI/CD workflow files. The version string SHALL NOT be hardcoded in workflow YAML files. CI workflows SHALL use `mvn help:evaluate` or a similar mechanism to resolve the version dynamically.

#### Scenario: Version bump requires only POM change
- **WHEN** the version is bumped in the root `pom.xml`
- **THEN** all CI workflows automatically use the new version without manual edits

### Requirement: JaCoCo coverage thresholds unified
All modules SHALL use the same JaCoCo instruction and branch coverage minimum of 80%. Module-specific overrides below 80% SHALL be removed unless explicitly documented with justification.

#### Scenario: Discord module meets 80% threshold
- **WHEN** the `nyt-scorebot-discord` module's coverage is checked
- **THEN** the minimum branch coverage threshold is 80% (not 60%)

### Requirement: Sonar project key aligned between POM and CI
The `sonar.projectKey` property SHALL have the same value in both the root `pom.xml` and the CI workflow file. The canonical value SHALL be `wandocorp-tech_nyt-scorebot`.

#### Scenario: Sonar key consistent
- **WHEN** the Sonar analysis runs
- **THEN** the project key `wandocorp-tech_nyt-scorebot` is used regardless of whether it comes from POM or CI override
