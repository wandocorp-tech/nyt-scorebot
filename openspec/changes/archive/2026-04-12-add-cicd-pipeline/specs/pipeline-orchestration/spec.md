## Purpose

Defines requirements for the pipeline workflow: orchestrating build, test, and deploy in sequence on pushes to `main`, with deploy and release gated to the `main` branch.

## ADDED Requirements

### Requirement: Pipeline orchestrates build, test, and deploy in sequence
The pipeline workflow SHALL call the build, test, and deploy workflows in sequence: build → test → deploy. Each subsequent job SHALL only run if the previous job succeeded.

#### Scenario: Full pipeline success
- **WHEN** the pipeline workflow runs and build, test, and deploy all succeed
- **THEN** the pipeline SHALL complete successfully with all three jobs marked as passed

#### Scenario: Test failure stops deploy
- **WHEN** the test job fails in the pipeline
- **THEN** the deploy job SHALL NOT run and the pipeline SHALL be marked as failed

#### Scenario: Build failure stops test and deploy
- **WHEN** the build job fails in the pipeline
- **THEN** neither the test job nor the deploy job SHALL run

### Requirement: Pipeline triggers on push to main
The pipeline workflow SHALL trigger automatically on pushes to the `main` branch.

#### Scenario: Push to main triggers pipeline
- **WHEN** a commit is pushed to the `main` branch
- **THEN** the full pipeline (build → test → deploy) SHALL be triggered

#### Scenario: Push to feature branch does not trigger pipeline
- **WHEN** a commit is pushed to a branch other than `main`
- **THEN** the pipeline workflow SHALL NOT be triggered

### Requirement: Pipeline supports manual trigger
The pipeline workflow SHALL be triggerable via `workflow_dispatch` for manual execution.

#### Scenario: Manual pipeline trigger
- **WHEN** a user triggers the pipeline workflow via GitHub Actions UI
- **THEN** the full pipeline (build → test → deploy) SHALL execute

### Requirement: Pipeline passes artifacts between jobs
The pipeline workflow SHALL ensure the JAR artifact built in the build job is available to the test and deploy jobs without rebuilding.

#### Scenario: Artifact reuse across pipeline jobs
- **WHEN** the build job completes successfully in the pipeline
- **THEN** the test and deploy jobs SHALL use the same JAR artifact produced by the build job
