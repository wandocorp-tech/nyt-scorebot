## MODIFIED Requirements

### Requirement: Release workflow reuses build artifact
The release workflow SHALL download the JAR artifact produced by the build job instead of rebuilding from source. The release SHALL NOT run `mvn package` or any Maven build command.

#### Scenario: Release uses pre-built JAR
- **WHEN** the release workflow runs after a successful build
- **THEN** it downloads the JAR artifact from the build job and attaches it to the GitHub release

#### Scenario: Release JAR matches build JAR
- **WHEN** the release artifact is compared to the build artifact
- **THEN** they are byte-identical (same JAR, not rebuilt)
