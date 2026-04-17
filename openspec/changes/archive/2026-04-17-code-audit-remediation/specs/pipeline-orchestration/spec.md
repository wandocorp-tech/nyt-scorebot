## MODIFIED Requirements

### Requirement: E2E tests restricted to main branch
End-to-end tests (which require a live Discord connection) SHALL only run on pushes to the `main` branch or via manual `workflow_dispatch`. They SHALL NOT run on pull request events or feature branch pushes.

#### Scenario: PR does not trigger E2E
- **WHEN** a pull request is opened or updated
- **THEN** the E2E test job is skipped

#### Scenario: Push to main triggers E2E
- **WHEN** a commit is pushed to `main`
- **THEN** the E2E test job runs

#### Scenario: Manual trigger runs E2E
- **WHEN** the pipeline is triggered via `workflow_dispatch`
- **THEN** the E2E test job runs
