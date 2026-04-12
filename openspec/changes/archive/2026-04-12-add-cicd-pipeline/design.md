## Context

NYT Scorebot is a multi-module Spring Boot Maven project (Java 17) that runs as a Discord bot on a Raspberry Pi. It is built locally and deployed manually via SSH. There are no existing CI/CD workflows, Dockerfiles, or deployment automation. The final artifact is a fat JAR at `nyt-scorebot-app/target/nyt-scorebot-app-1.0-SNAPSHOT.jar`. The Pi runs the JAR as a long-lived process and is reachable over SSH.

The project uses JaCoCo for coverage enforcement (≥80% instruction + branch) and Mockito with ByteBuddy for mocking final Discord4J classes. The `SmokeTest` class is obsolete and superseded by `EndToEndTest`, which requires a live Discord connection and database. Unit tests run without any live connections.

## Goals / Non-Goals

**Goals:**
- Automate build (with unit tests), end-to-end testing, and deploy as separate GitHub Actions jobs that can run independently or be chained into a pipeline.
- The **build** job compiles and runs unit tests (excluding `EndToEndTest`), enforcing JaCoCo coverage.
- The **test** job runs the `EndToEndTest` against a live Discord connection.
- Delete the obsolete `SmokeTest.java`.
- Deploy the JAR to the Raspberry Pi over SSH, replacing the running application and restarting it.
- Provide a standalone release workflow for creating tagged GitHub Releases with the JAR attached.
- Keep workflows simple, maintainable, and idiomatic to GitHub Actions.

**Non-Goals:**
- Containerizing the application (Docker) — the Pi runs the JAR directly.
- Multi-environment deployments (staging, production) — there is one Pi.
- Automated rollback — manual intervention is acceptable for a personal project.
- Modifying application code, configuration, or build plugins.

## Decisions

### 1. Workflow file structure: reusable workflows + pipeline caller

**Decision:** Define build, test, and deploy as **reusable workflows** (`workflow_call`) in separate YAML files. A pipeline workflow calls them in sequence. Each reusable workflow also supports `workflow_dispatch` for standalone execution.

**Rationale:** This gives maximum flexibility — each job is independently triggerable for debugging, and the pipeline composes them without duplicating steps. GitHub Actions natively supports `workflow_call` for this pattern.

**Alternatives considered:**
- *Single workflow with multiple jobs:* Simpler file structure, but individual jobs can't be triggered independently without `workflow_dispatch` + conditional logic, which gets messy.
- *Composite actions:* These are meant for reusable steps within a job, not full jobs with their own runners — doesn't fit the "separate runnable jobs" requirement.

### 2. Artifact passing between jobs: GitHub Actions artifacts

**Decision:** The build job uploads the JAR via `actions/upload-artifact`. Test and deploy jobs download it via `actions/download-artifact`. This avoids rebuilding in every job.

**Rationale:** Build-once-deploy-many is the standard CI/CD pattern. GitHub Actions artifacts are the native mechanism for passing files between jobs/workflows.

### 3. SSH deployment mechanism: `appleboy/ssh-action` + `appleboy/scp-action`

**Decision:** Use the well-established `appleboy/scp-action` to copy the JAR and `appleboy/ssh-action` to run restart commands on the Pi.

**Rationale:** These are the most widely used SSH actions in the GitHub Actions ecosystem, well-maintained, and avoid the need to manually script SSH key setup. They support key-based auth, configurable ports, and timeouts.

**Alternatives considered:**
- *Manual SSH via `ssh` command:* Requires manually managing known_hosts, key files, and permissions in workflow steps — more error-prone.
- *Self-hosted runner on the Pi:* Eliminates SSH but adds overhead of running a runner agent on a resource-constrained device.

### 4. Service management on the Pi: systemd

**Decision:** Assume the application is managed by a systemd service unit on the Pi. The deploy job runs `sudo systemctl restart <service-name>` after copying the new JAR.

**Rationale:** systemd is the standard service manager on Raspberry Pi OS (Debian-based). It provides automatic restart on failure, log integration via journald, and clean process lifecycle management. The service unit file itself is not managed by this CI/CD pipeline — it's a one-time setup on the Pi.

### 5. Release workflow: manual trigger with version tag

**Decision:** The release workflow is triggered solely via `workflow_dispatch` with a version tag input. It builds the project, creates a Git tag, and publishes a GitHub Release with the JAR attached.

**Rationale:** Releases are intentional, infrequent events for a personal project. A manual trigger with an explicit version tag avoids accidental releases and keeps the workflow simple. This workflow is completely independent of the pipeline.

### 6. Pipeline triggers

**Decision:** The pipeline workflow triggers on push to `main` and via `workflow_dispatch`. Feature branches do not trigger the full pipeline.

**Rationale:** For a solo-developer project, push-to-main is the primary integration point. `workflow_dispatch` allows manual re-runs. Branch-based CI can be added later if needed.

### 7. Test split: unit tests in build, E2E in test

**Decision:** The build job runs `mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` to compile, run unit tests, and enforce JaCoCo coverage. The test job runs `mvn test -Dtest=com.wandocorp.nytscorebot.EndToEndTest` to execute the end-to-end test against a live Discord connection.

**Rationale:** Unit tests are fast, deterministic, and require no external services — they belong with the build to catch regressions immediately. The E2E test requires a live Discord bot token, test channels, and a database, so it runs in a dedicated job with the appropriate secrets. This separation also means the build can succeed and produce a deployable artifact even if the E2E environment is temporarily unavailable.

**Alternatives considered:**
- *All tests in one job:* Simpler but couples the build artifact to E2E availability. A flaky Discord connection would block deployments.

### 8. Deleting SmokeTest

**Decision:** Delete `SmokeTest.java` entirely. The `EndToEndTest` covers the same scenarios and more.

**Rationale:** Maintaining two overlapping live-Discord test suites adds confusion and cost. The SmokeTest was the original integration test; EndToEndTest supersedes it with better coverage of the full day scenario.

### 9. Repository secrets

The following GitHub repository secrets are required:

| Secret | Purpose |
|---|---|
| `PI_SSH_KEY` | SSH private key for authenticating to the Pi |
| `PI_HOST` | Hostname or IP address of the Pi |
| `PI_USER` | SSH username on the Pi |
| `PI_SSH_PORT` | SSH port (defaults to 22 if not set) |
| `PI_DEPLOY_PATH` | Remote directory where the JAR is placed (e.g., `/opt/scorebot/`) |
| `PI_SERVICE_NAME` | systemd service name to restart (e.g., `scorebot`) |
| `DISCORD_TOKEN` | Discord bot token for E2E tests (same token used in production) |

## Risks / Trade-offs

- **[Risk] Pi is unreachable during deploy** → The deploy job will fail with a timeout. This is acceptable — re-run the pipeline or deploy job once the Pi is back online. No partial state is left since the JAR is copied first, then the service is restarted.

- **[Risk] Secret rotation** → If the SSH key is rotated on the Pi, the GitHub secret must be updated manually. Document the required secrets clearly.

- **[Risk] Disk space on Pi** → Each deploy overwrites the JAR in-place at the configured path. No old artifacts accumulate.

- **[Trade-off] No health check after deploy** → The pipeline doesn't verify the application started successfully. Adding a post-deploy health check (e.g., curl a status endpoint) could be a future enhancement, but the bot doesn't expose an HTTP endpoint currently.

- **[Trade-off] H2 file database on Pi** → The file-based H2 database is persisted on disk. Deploys restart the service but don't touch the database file, so data is preserved. However, schema changes via `ddl-auto=update` run on startup — breaking schema changes could cause startup failures.
