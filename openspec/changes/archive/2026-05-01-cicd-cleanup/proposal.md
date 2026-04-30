## Why

The CI/CD pipeline has accumulated complexity — AI-generated release notes, a SonarCloud integration, and a manual production approval gate — that is no longer adding value. Simplifying these removes external dependencies, reduces pipeline time, and lowers maintenance overhead.

## What Changes

- **Release notes**: Replace AI-generated release notes (GitHub Models) with the raw conventional-commit log. Remove the "Full release notes" footer link from Discord announcements.
- **SonarCloud**: Remove the SonarCloud scan step, `SONAR_TOKEN` secret, and all `sonar.*` properties from `pom.xml`.
- **Deploy approval**: Remove the `environment: production` gate from the deploy workflow so pushes to `main` deploy without manual approval.
- **E2E test logging**: Log the channel name and message content of every message sent during the E2E test, rendered as a markdown table in the GitHub Actions step summary.
- **E2E test scope**: The E2E test SHALL only run on pull request builds. It is skipped on pushes to `main`. The `release` job is re-wired to depend on `build` rather than `test` so the main-branch pipeline is unblocked.

## Capabilities

### New Capabilities

*(none)*

### Modified Capabilities

- `ai-release-notes`: Requirements removed — release notes are no longer AI-generated; the raw commit log replaces the model output. The `models: read` permission and GitHub Models API call are eliminated.
- `discord-release-announcement`: Message format requirement changes — the footer link (`📋 Full release notes →`) is removed from the announcement message.
- `ci-build-test`: Sonar-related requirements removed — the Sonar scan step, `SONAR_TOKEN` secret, and `sonar.*` POM properties are all removed.
- `deploy-to-pi`: Approval gate removed — the `environment: production` constraint is removed so the deploy job runs without a manual review step.
- `pipeline-orchestration`: E2E test scoped to PRs only; `release` job re-wired to depend on `build` so the main-branch pipeline no longer depends on the skipped test job.

## Impact

- `.github/workflows/release.yml` — remove AI generation steps, drop `models: read` permission, use `commits.txt` as release body
- `.github/workflows/announce.yml` — remove `release_url` input, remove footer from message, fix truncation overhead calculation
- `.github/workflows/pipeline.yml` — remove `SONAR_TOKEN`, `models: read`, `release_url`; add `if: github.event_name == 'pull_request'` to test job; change `release: needs: test` → `needs: build`
- `.github/workflows/build.yml` — remove SonarCloud step and `SONAR_TOKEN` secret declaration
- `.github/workflows/deploy.yml` — remove `environment: production`
- `pom.xml` — remove 5 `sonar.*` properties
- `EndToEndTest.java` — add `@Slf4j`, cast to `GuildMessageChannel`, write GHA step summary markdown table in `postTo()`
