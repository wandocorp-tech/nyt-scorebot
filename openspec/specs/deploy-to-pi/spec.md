# deploy-to-pi Specification

## Purpose
TBD - created by archiving change add-cicd-pipeline. Update Purpose after archive.
## Requirements
### Requirement: Deploy JAR to Raspberry Pi via SCP
The deploy workflow SHALL copy the built JAR file to the Raspberry Pi at the path specified by the `PI_DEPLOY_PATH` secret using SCP over SSH.

#### Scenario: Successful file transfer
- **WHEN** the deploy workflow runs with a valid JAR artifact and the Pi is reachable
- **THEN** the JAR file SHALL be copied to `$PI_DEPLOY_PATH` on the Pi via SCP

#### Scenario: Pi unreachable
- **WHEN** the deploy workflow attempts to connect and the Pi is not reachable
- **THEN** the workflow SHALL fail with a connection timeout error

### Requirement: Restart application service after deploy
The deploy workflow SHALL restart the systemd service on the Pi after copying the new JAR.

#### Scenario: Successful restart
- **WHEN** the JAR has been successfully copied to the Pi
- **THEN** the workflow SHALL execute `sudo systemctl restart $PI_SERVICE_NAME` on the Pi via SSH

#### Scenario: Service restart failure
- **WHEN** the systemd restart command returns a non-zero exit code
- **THEN** the deploy workflow SHALL fail

### Requirement: Deploy uses repository secrets for SSH configuration
The deploy workflow SHALL use GitHub repository secrets for all SSH connection parameters to avoid hardcoding credentials.

#### Scenario: Secrets used for connection
- **WHEN** the deploy job establishes an SSH connection
- **THEN** it SHALL use `PI_SSH_KEY`, `PI_HOST`, `PI_USER`, and `PI_SSH_PORT` secrets for authentication and connection

### Requirement: Deploy standalone triggering
The deploy workflow SHALL be independently triggerable via `workflow_dispatch`.

#### Scenario: Manual deploy trigger
- **WHEN** a user triggers the deploy workflow via GitHub Actions UI
- **THEN** the deploy job SHALL build the project, then deploy the JAR to the Pi

### Requirement: Deploy downloads build artifact in pipeline context
When called from the pipeline workflow, the deploy job SHALL use the JAR artifact from the build job rather than rebuilding.

#### Scenario: Deploy in pipeline context
- **WHEN** the deploy workflow is called from the pipeline with a build artifact available
- **THEN** the deploy job SHALL download and deploy that artifact without rebuilding

