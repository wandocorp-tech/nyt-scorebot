# release-creation Specification

## Purpose
TBD - created by archiving change add-cicd-pipeline. Update Purpose after archive.
## Requirements
### Requirement: Release workflow creates a GitHub Release
The release workflow SHALL create a GitHub Release with a version tag derived from the pipeline run number and attach the built JAR as a release asset. The release body SHALL be populated from AI-generated release notes (see `ai-release-notes` capability) rather than GitHub's auto-generated notes.

#### Scenario: Successful release creation
- **WHEN** the release workflow is triggered with a version tag (e.g., `v42`)
- **THEN** a Git tag SHALL be created, a GitHub Release SHALL be published with that tag, the `nyt-scorebot-app-1.0-SNAPSHOT.jar` SHALL be attached as a release asset, and the release body SHALL contain AI-generated notes

#### Scenario: AI notes used instead of GitHub auto notes
- **WHEN** the release workflow creates the GitHub Release
- **THEN** `generate_release_notes` SHALL be `false` and the `body` field SHALL contain the AI-generated notes string

### Requirement: Release workflow is part of the pipeline
The release workflow SHALL run as a stage of the main CI/CD pipeline, between `test` and `deploy`. It SHALL also remain triggerable independently via `workflow_dispatch`.

#### Scenario: Release runs in pipeline before deploy
- **WHEN** the pipeline runs on a push to `main`
- **THEN** the release job SHALL run after `test` succeeds and before `deploy` starts

#### Scenario: Manual release trigger still supported
- **WHEN** a user triggers the release workflow via `workflow_dispatch` and provides a version tag
- **THEN** the release workflow SHALL build the project (or download the JAR artifact), generate AI notes, and create the release independently of the pipeline

### Requirement: Release builds the project
The release workflow SHALL produce or reuse the built JAR before creating the release.

#### Scenario: Build before release in standalone mode
- **WHEN** the release workflow is triggered via `workflow_dispatch` and no JAR artifact is available
- **THEN** it SHALL run `mvn clean package -DskipTests` to produce the JAR before attaching it to the release

#### Scenario: Reuse pipeline artifact
- **WHEN** the release workflow is invoked from the pipeline
- **THEN** it SHALL download the JAR artifact produced by the build job rather than rebuilding

