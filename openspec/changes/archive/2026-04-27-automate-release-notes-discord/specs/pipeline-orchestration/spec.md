## MODIFIED Requirements

### Requirement: Pipeline orchestrates build, test, release, deploy, and announce in sequence
The pipeline workflow SHALL call the build, test, release, deploy, and announce workflows in sequence: `build → test → release → deploy → announce`. Each subsequent job SHALL only run if the previous job succeeded, except `announce`, which SHALL run after `deploy` succeeds but SHALL NOT cause the pipeline to fail if it itself fails.

#### Scenario: Full pipeline success
- **WHEN** the pipeline workflow runs and all five jobs succeed
- **THEN** the pipeline SHALL complete successfully with all jobs marked as passed

#### Scenario: Test failure stops release, deploy, and announce
- **WHEN** the test job fails in the pipeline
- **THEN** the release, deploy, and announce jobs SHALL NOT run and the pipeline SHALL be marked as failed

#### Scenario: Build failure stops everything downstream
- **WHEN** the build job fails in the pipeline
- **THEN** none of test, release, deploy, or announce SHALL run

#### Scenario: Release failure stops deploy and announce
- **WHEN** the release job fails (e.g., GitHub Models unavailable)
- **THEN** the deploy and announce jobs SHALL NOT run

#### Scenario: Announce failure does not fail the pipeline
- **WHEN** the announce job fails (e.g., Discord webhook unreachable)
- **THEN** the deploy is already complete and the pipeline SHALL be marked as successful

### Requirement: Pipeline triggers on push to main
The pipeline workflow SHALL trigger automatically on pushes to the `main` branch.

#### Scenario: Push to main triggers pipeline
- **WHEN** a commit is pushed to the `main` branch
- **THEN** the full pipeline (`build → test → release → deploy → announce`) SHALL be triggered

#### Scenario: Push to feature branch does not trigger release/deploy/announce
- **WHEN** a commit is pushed to a branch other than `main`
- **THEN** the release, deploy, and announce jobs SHALL NOT be triggered

### Requirement: Pipeline supports manual trigger
The pipeline workflow SHALL be triggerable via `workflow_dispatch` for manual execution.

#### Scenario: Manual pipeline trigger
- **WHEN** a user triggers the pipeline workflow via GitHub Actions UI
- **THEN** the full pipeline SHALL execute

### Requirement: Pipeline passes artifacts between jobs
The pipeline workflow SHALL ensure the JAR artifact built in the build job is available to the test, release, and deploy jobs without rebuilding.

#### Scenario: Artifact reuse across pipeline jobs
- **WHEN** the build job completes successfully in the pipeline
- **THEN** the test, release, and deploy jobs SHALL use the same JAR artifact produced by the build job
