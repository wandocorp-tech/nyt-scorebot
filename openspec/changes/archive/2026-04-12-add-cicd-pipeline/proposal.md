## Why

The project has no automated build, test, or deployment pipeline. Every release requires manually building the JAR, copying it to the Raspberry Pi via SSH, and restarting the service. Adding GitHub Actions CI/CD eliminates manual steps, catches regressions early, and enables one-click deploys to the Pi.

## What Changes

- **Delete `SmokeTest.java`** — it has been superseded by `EndToEndTest` and is no longer needed.
- Add a GitHub Actions **build** job that compiles the multi-module Maven project, runs the **unit tests** (excluding `EndToEndTest`), enforces JaCoCo coverage (≥80%), and produces the Spring Boot executable JAR.
- Add a GitHub Actions **test** job that runs the **end-to-end test** (`EndToEndTest`) against a live Discord connection and database.
- Add a GitHub Actions **deploy** job that copies the JAR to the Raspberry Pi via SSH and restarts the application service.
- Add a GitHub Actions **pipeline** workflow that orchestrates build → test → deploy as a single end-to-end flow, while keeping each job independently runnable via `workflow_dispatch`.
- Add a GitHub Actions **release** workflow that creates a GitHub Release with the built JAR attached, triggered manually and not part of the main pipeline.
- Add repository secret documentation for SSH credentials, Pi connection details, and Discord bot token for E2E tests.

## Capabilities

### New Capabilities
- `ci-build-test`: GitHub Actions build job that compiles, runs unit tests (excluding EndToEndTest), and enforces JaCoCo coverage; plus a test job that runs the EndToEndTest against live Discord. Both usable standalone or as pipeline stages.
- `remove-smoke-test`: Delete the obsolete `SmokeTest.java` class, which has been superseded by `EndToEndTest`.
- `deploy-to-pi`: GitHub Actions job that deploys the Spring Boot JAR to a Raspberry Pi over SSH and restarts the service.
- `release-creation`: Standalone GitHub Actions workflow for creating tagged GitHub Releases with the JAR artifact attached.
- `pipeline-orchestration`: A composite pipeline workflow that chains build → test → deploy, triggered on push to main or manually.

### Modified Capabilities

_None — this change introduces new CI/CD infrastructure without modifying existing application code or specs._

## Impact

- **New files:** GitHub Actions workflow YAML files under `.github/workflows/`.
- **Repository secrets required:** SSH private key, Pi host/user/port, remote deploy path, and optionally the service name for restart.
- **Code deletion:** `SmokeTest.java` is removed. All SmokeTest exclusion flags (`-Dtest='!...SmokeTest'`) in documentation and commands become unnecessary.
- **Existing Maven test targets** are used — build job runs `mvn verify` excluding `EndToEndTest`; test job runs only `EndToEndTest`.
- **Dependencies:** Relies on the Pi having Java 17+ installed and an SSH server running. A systemd service (or equivalent) should manage the application process on the Pi.
