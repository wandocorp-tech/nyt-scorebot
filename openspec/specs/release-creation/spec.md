# release-creation Specification

## Purpose
TBD - created by archiving change add-cicd-pipeline. Update Purpose after archive.
## Requirements
### Requirement: Release workflow creates a GitHub Release
The release workflow SHALL create a GitHub Release with a user-specified version tag and attach the built JAR as a release asset.

#### Scenario: Successful release creation
- **WHEN** the release workflow is triggered with a version tag (e.g., `v1.0.0`)
- **THEN** a Git tag SHALL be created, a GitHub Release SHALL be published with that tag, and the `nyt-scorebot-app-1.0-SNAPSHOT.jar` SHALL be attached as a release asset

### Requirement: Release triggered only via workflow_dispatch
The release workflow SHALL only be triggerable via `workflow_dispatch` with a required version tag input.

#### Scenario: Manual release trigger
- **WHEN** a user triggers the release workflow via GitHub Actions UI and provides a version tag
- **THEN** the release workflow SHALL build the project and create the release

#### Scenario: No automatic triggering
- **WHEN** code is pushed to any branch
- **THEN** the release workflow SHALL NOT be triggered

### Requirement: Release is independent of the pipeline
The release workflow SHALL NOT be part of the main CI/CD pipeline. It SHALL be a completely separate workflow.

#### Scenario: Pipeline runs without release
- **WHEN** the pipeline workflow runs (build → test → deploy)
- **THEN** the release workflow SHALL NOT be triggered as part of that pipeline

### Requirement: Release builds the project
The release workflow SHALL build the Maven project to produce a fresh JAR before creating the release.

#### Scenario: Build before release
- **WHEN** the release workflow is triggered
- **THEN** it SHALL run `mvn clean package -DskipTests` to produce the JAR before attaching it to the release

