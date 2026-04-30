## ADDED Requirements

### Requirement: Deploy runs without manual approval gate
The deploy workflow SHALL NOT require a manual approval step. Removing the `environment: production` key from the deploy job eliminates the GitHub Actions environment protection rule that previously gated deployment.

#### Scenario: Deploy runs automatically after release
- **WHEN** the release job completes successfully on a push to `main`
- **THEN** the deploy job SHALL start immediately without waiting for a manual reviewer to approve

#### Scenario: No environment context on deploy job
- **WHEN** the deploy workflow runs
- **THEN** the deploy job SHALL NOT have an `environment:` key and SHALL NOT be subject to environment protection rules
