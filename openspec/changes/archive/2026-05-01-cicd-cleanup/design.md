## Context

The pipeline today runs five jobs: `build` (Maven verify + SonarCloud scan), `test` (E2E), `release` (GitHub Models AI notes → GitHub Release), `deploy` (environment: production approval gate → Pi), and `announce` (fetch release body → Discord webhook). This change removes the AI summarisation, SonarCloud integration, and production approval gate, and adds GHA step-summary logging to the E2E test.

## Goals / Non-Goals

**Goals:**
- Replace AI-generated release notes with the raw conventional-commit log
- Remove SonarCloud entirely (workflow step, secret, POM properties)
- Remove the `environment: production` manual approval gate
- Log E2E test messages as a GitHub Actions step summary markdown table

**Non-Goals:**
- Changing overall pipeline shape (`build → test → release → deploy → announce`)
- Changing the deployment target or SSH mechanism
- Filtering or reformatting commits before posting to Discord

## Decisions

### D1 — Use `commits.txt` directly as the GitHub Release body

The commit-collection step in `release.yml` already writes `commits.txt` with one `- subject` line per commit. Passing it as `body_path` to `softprops/action-gh-release` is sufficient — no new step needed. The `announce.yml` workflow already fetches the release body via `gh release view`, so it picks up the commit log automatically with no changes to that step.

**Alternatives considered:**
- Generating the commit log inside `announce.yml` instead: would bypass the GitHub Release as a source of truth and add a `fetch-depth: 0` checkout to announce. Rejected — keeping the release as single source of truth is cleaner.

### D2 — Remove AI generation steps entirely, drop `models: read`

The `Collect commits since last release` step is kept (it produces `commits.txt`). The `Generate release notes via GitHub Models` step is removed. The `models: read` permission is removed from both `release.yml` and the `release` job in `pipeline.yml`.

### D3 — Remove footer by dropping `release_url` input end-to-end

The `release_url` input is threaded through `pipeline.yml` → `announce.yml`. Removing it from both files eliminates the footer entirely. The Discord message truncation overhead calculation in `announce.yml` must be updated to account for only header + separator, not header + footer + separator.

### D4 — Remove `environment: production` fully (not just protection rules)

Removing the key entirely means the job has no environment context, eliminating both the approval gate and any environment-specific variable scope. This is simpler than keeping the environment without protection rules.

**Alternatives considered:**
- Keep the environment but remove protection rules in GitHub settings: requires a manual UI step outside this change and leaves the environment association. Rejected — full removal is cleaner and self-contained.

### D5 — E2E logging via GHA step summary with SLF4J fallback

GitHub Actions exposes `$GITHUB_STEP_SUMMARY` as a file path. Writing markdown to that file renders a formatted table in the Actions UI. The `postTo()` helper will:
1. Write a markdown table row (`| channel | content |`) to the file at `GITHUB_STEP_SUMMARY` when that env var is set.
2. Fall back to `log.info("[{}] >>> {}", channelName, content)` via `@Slf4j` when running locally (where `GITHUB_STEP_SUMMARY` is absent).

The step summary file is opened in append mode so multiple `postTo()` calls accumulate rows. A header row (`| Channel | Message |` + separator) is written on the first call (when the file is initially empty or missing). Casting `MessageChannel` → `GuildMessageChannel` gives access to `getName()` for the channel label.

**Alternatives considered:**
- GHA workflow commands (`::notice::` etc.): visible in log stream but not a persistent summary view. Rejected — step summary is more readable for test output.
- Writing a separate summary step in `test.yml` shell: would require exporting data from Java, adding complexity. Rejected — writing directly from Java is simpler.

### D6 — E2E test runs on PRs only; `release` depends on `build`, not `test`

Adding `if: github.event_name == 'pull_request'` to the `test` job skips it on main-branch pushes. The `release` job currently has `needs: test` — if `test` is skipped, GitHub Actions skips `release` too (and everything downstream). Fix: change `release: needs: [test]` → `needs: [build]`. The release already has `if: github.ref == 'refs/heads/main'` so it never runs on PRs regardless.

Resulting flows:
- **PR**: `build → test` (release/deploy/announce skipped by their `if` condition)
- **Main**: `build → release → deploy → announce` (test skipped by its `if` condition)

**Alternatives considered:**
- `if: needs.test.result == 'success' || needs.test.result == 'skipped'` on release: works but fragile and verbose. Rejected — changing the `needs` edge is cleaner.

## Risks / Trade-offs

- **Raw commit log includes housekeeping commits** — `chore:`, `ci:`, `test:` commits will appear in the Discord announcement. Accepted trade-off; the conventional-commit prefix makes them easy to read.
- **Sonar quality gate removed** — ongoing code-quality tracking is lost. Acceptable given the project has JaCoCo coverage enforcement as a compensating control.
- **No deploy approval** — production deployments run automatically on every `main` push. Acceptable for a small single-operator bot.
- **`GITHUB_STEP_SUMMARY` not set locally** → SLF4J fallback activates silently; test still passes.
- **`GuildMessageChannel` cast** — valid for all E2E channels (they are all guild text channels); would panic if a DM channel were used, which is not a scenario in the E2E test.
