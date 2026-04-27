## ADDED Requirements

### Requirement: Release notes generated from commit history via GitHub Models
The release workflow SHALL generate release-notes content by sending the commit history since the previous tag to the GitHub Models inference API and using the model's response as the release body.

#### Scenario: Commits since last tag summarised
- **WHEN** the release workflow runs and a previous tag exists
- **THEN** the workflow SHALL collect all commit messages between the previous tag and `HEAD`, send them to `https://models.github.ai/inference/chat/completions`, and capture the model's response as the release notes

#### Scenario: First release has no previous tag
- **WHEN** the release workflow runs and `git describe --tags --abbrev=0` returns no tag
- **THEN** the workflow SHALL fall back to using the most recent 50 commits as input to the model

### Requirement: Authentication via built-in token
The release workflow SHALL authenticate to GitHub Models using the workflow's `GITHUB_TOKEN`, with the `models: read` permission granted on the job.

#### Scenario: No external API key required
- **WHEN** the release workflow calls the GitHub Models endpoint
- **THEN** the `Authorization: Bearer ${GITHUB_TOKEN}` header SHALL be used and no `OPENAI_API_KEY` or other external secret SHALL be required

### Requirement: Prompt filters housekeeping commits
The prompt sent to the model SHALL instruct it to omit housekeeping commits (those with `chore:`, `ci:`, `test:`, or `build:` prefixes) from the output unless they have user-visible impact.

#### Scenario: Housekeeping commits excluded
- **WHEN** the commit list includes commits prefixed with `chore:`, `ci:`, `test:`, or `build:`
- **THEN** the model output SHALL omit those commits from the bullet list

### Requirement: Output constrained for Discord
The prompt SHALL instruct the model to produce a concise bullet list of ≤ 1500 characters suitable for posting to Discord.

#### Scenario: Output kept concise
- **WHEN** the model produces release notes
- **THEN** the output SHALL be formatted as bullet points and target ≤ 1500 characters total

### Requirement: AI notes used as GitHub Release body
The AI-generated notes SHALL be used as the body of the GitHub Release created by the release workflow.

#### Scenario: GitHub Release contains AI notes
- **WHEN** the release workflow creates the GitHub Release
- **THEN** the release `body` SHALL contain the AI-generated notes (not GitHub's `generate_release_notes` output)
