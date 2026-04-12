## 1. Remove SmokeTest

- [x] 1.1 Delete `nyt-scorebot-app/src/test/java/com/wandocorp/nytscorebot/SmokeTest.java` from the codebase.
- [x] 1.2 Update `README.md` and `.github/copilot-instructions.md` to replace SmokeTest exclusion references with EndToEndTest exclusion (e.g., `-Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` for unit test runs).

## 2. Build Workflow

- [x] 2.1 Create `.github/workflows/build.yml` — a reusable workflow (`workflow_call`) that also supports `workflow_dispatch`. Sets up Java 17 (temurin), caches Maven dependencies, runs `mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` to compile, run unit tests, and enforce JaCoCo coverage (≥80%). Uploads `nyt-scorebot-app/target/nyt-scorebot-app-1.0-SNAPSHOT.jar` as a GitHub Actions artifact.

## 3. Test Workflow

- [x] 3.1 Create `.github/workflows/test.yml` — a reusable workflow (`workflow_call`) that also supports `workflow_dispatch`. Runs `mvn test -Dtest=com.wandocorp.nytscorebot.EndToEndTest` to execute the end-to-end test against a live Discord connection. Uses the `DISCORD_TOKEN` secret. When standalone, builds from source first. Uses Java 17 (temurin) with Maven caching.

## 4. Deploy Workflow

- [x] 4.1 Create `.github/workflows/deploy.yml` — a reusable workflow (`workflow_call`) that also supports `workflow_dispatch`. When called from the pipeline, downloads the build artifact; when standalone, builds from source. Uses `appleboy/scp-action` to copy the JAR to `$PI_DEPLOY_PATH` on the Pi, then `appleboy/ssh-action` to run `sudo systemctl restart $PI_SERVICE_NAME`. Uses secrets: `PI_SSH_KEY`, `PI_HOST`, `PI_USER`, `PI_SSH_PORT`, `PI_DEPLOY_PATH`, `PI_SERVICE_NAME`.

## 5. Pipeline Workflow

- [x] 5.1 Create `.github/workflows/pipeline.yml` — orchestrates build → test → deploy by calling the reusable workflows in sequence with `needs` dependencies. Triggers on push to `main` and `workflow_dispatch`. Passes the build artifact to test and deploy jobs.

## 6. Release Workflow

- [x] 6.1 Create `.github/workflows/release.yml` — standalone workflow triggered only via `workflow_dispatch` with a required `version` input. Builds the project with Java 17, creates a Git tag for the version, and publishes a GitHub Release with the JAR attached using `softprops/action-gh-release`. Not part of the pipeline.

## 7. Documentation

- [x] 7.1 Update `README.md` with a CI/CD section documenting the workflows, required repository secrets (including `DISCORD_TOKEN` for E2E tests), Pi prerequisites (Java 17, systemd service unit), and how to trigger each workflow manually.
