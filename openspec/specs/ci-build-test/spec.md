## ADDED Requirements

### Requirement: E2E test logs messages as GitHub Actions step summary
The E2E test SHALL log every message sent via `postTo()` as a markdown table in the GitHub Actions step summary. When `GITHUB_STEP_SUMMARY` is not set (local execution), it SHALL fall back to SLF4J logging.

#### Scenario: Messages logged as markdown table in GHA
- **WHEN** the E2E test runs in a GitHub Actions environment (`GITHUB_STEP_SUMMARY` env var is set)
- **THEN** each call to `postTo()` SHALL append a row to a markdown table in the step summary file, with columns `Channel` (the Discord channel name) and `Message` (the full message content)

#### Scenario: Table header written once
- **WHEN** the first `postTo()` call in the test run writes to the step summary
- **THEN** a markdown table header (`| Channel | Message |` and the separator row) SHALL be written before the first data row

#### Scenario: Local fallback to SLF4J
- **WHEN** the E2E test runs locally and `GITHUB_STEP_SUMMARY` is not set
- **THEN** each `postTo()` call SHALL log the channel name and message content via SLF4J at INFO level

## REMOVED Requirements

### Requirement: Sonar project key aligned between POM and CI
**Reason**: SonarCloud integration is removed entirely. The `sonar.*` POM properties and the SonarCloud scan step in the build workflow are deleted.
**Migration**: Remove the `sonar.maven.plugin.version`, `sonar.projectKey`, `sonar.organization`, `sonar.host.url`, and `sonar.coverage.jacoco.xmlReportPaths` properties from `pom.xml`. Remove the `SonarCloud scan` step and `SONAR_TOKEN` secret from `build.yml`. Remove the `SONAR_TOKEN` secret from the build job in `pipeline.yml`.

#### Scenario: Build completes without Sonar scan
- **WHEN** the build workflow runs
- **THEN** no `mvn sonar:sonar` command SHALL be executed and no `SONAR_TOKEN` secret SHALL be required
