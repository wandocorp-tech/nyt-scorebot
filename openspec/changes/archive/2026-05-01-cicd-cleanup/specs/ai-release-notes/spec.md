## REMOVED Requirements

### Requirement: Release notes generated from commit history via GitHub Models
**Reason**: AI summarisation adds external API dependency and `models: read` permission with no proportionate benefit. The raw conventional-commit log is sufficient and more predictable.
**Migration**: The raw commit log (one `- subject` line per commit since the previous tag) is used directly as the GitHub Release body. No external API call is made.

#### Scenario: Commits since last tag used as release body
- **WHEN** the release workflow runs and a previous tag exists
- **THEN** the workflow SHALL collect all commit messages between the previous tag and `HEAD` and write them as the release body without sending them to any external model API

#### Scenario: First release falls back to last 50 commits
- **WHEN** the release workflow runs and `git describe --tags --abbrev=0` returns no tag
- **THEN** the workflow SHALL fall back to using the most recent 50 commits as the release body

### Requirement: Authentication via built-in token
**Reason**: No longer needed — the GitHub Models API call is removed.
**Migration**: The `models: read` permission is removed from the release workflow job and from the pipeline orchestration job that calls it.

#### Scenario: No external API key required
- **WHEN** the release workflow runs
- **THEN** it SHALL NOT require `models: read` permission or call `https://models.github.ai/inference/chat/completions`

### Requirement: Prompt filters housekeeping commits
**Reason**: Removed along with the AI summarisation step.
**Migration**: Housekeeping commits (`chore:`, `ci:`, `test:`, `build:`) will appear in the raw commit log. Their conventional-commit prefixes make them identifiable.

#### Scenario: Housekeeping commits appear in raw log
- **WHEN** the commit list includes commits prefixed with `chore:`, `ci:`, `test:`, or `build:`
- **THEN** those commits SHALL appear in the release body as-is

### Requirement: Output constrained for Discord
**Reason**: Removed along with the AI summarisation step. Truncation is handled downstream in the announce workflow.
**Migration**: The announce workflow truncates the release body to fit Discord's 2000-character limit.

#### Scenario: Output may exceed 1500 characters
- **WHEN** there are many commits since the last release
- **THEN** the release body MAY exceed 1500 characters; the announce workflow is responsible for truncation

### Requirement: AI notes used as GitHub Release body
**Reason**: Replaced — the raw commit log is used as the release body instead.
**Migration**: The `body_path` in `softprops/action-gh-release` points to `commits.txt` rather than `release-notes.md`.

#### Scenario: GitHub Release contains raw commit log
- **WHEN** the release workflow creates the GitHub Release
- **THEN** the release `body` SHALL contain the raw commit log and NOT contain AI-generated text
