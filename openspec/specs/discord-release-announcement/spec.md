# discord-release-announcement Specification

## Purpose
TBD - created by archiving change automate-release-notes-discord. Update Purpose after archive.

## Requirements

### Requirement: Release notes posted to dedicated Discord channel after deploy
After a successful deploy to the Raspberry Pi, the pipeline SHALL post a release-notes announcement to a dedicated Discord channel via a webhook URL.

#### Scenario: Successful deploy triggers Discord announcement
- **WHEN** the deploy job completes successfully on a push to `main`
- **THEN** an announce job SHALL POST a message to the Discord webhook URL stored in the `DISCORD_RELEASE_WEBHOOK_URL` repository secret

#### Scenario: Failed deploy does not trigger announcement
- **WHEN** the deploy job fails
- **THEN** the announce job SHALL NOT run and no Discord message SHALL be posted

### Requirement: Announcement message format
The Discord announcement message SHALL be a plain-markdown message containing the version header, a concise bullet list of changes, and a link to the GitHub Release.

#### Scenario: Message structure
- **WHEN** the announce job posts to Discord
- **THEN** the message SHALL begin with `🚀 nyt-scorebot v{N} deployed!`, contain the AI-generated bullet list, and end with a `📋 Full release notes →` link to the GitHub Release URL for that version

#### Scenario: Message stays within Discord size limit
- **WHEN** the announce job posts to Discord
- **THEN** the total message length SHALL be ≤ 2000 characters; if the AI output would exceed this, it SHALL be truncated with an ellipsis before posting

### Requirement: Webhook authentication only
The announce job SHALL authenticate to Discord using only the webhook URL and SHALL NOT have access to the bot token.

#### Scenario: Bot token not exposed
- **WHEN** the announce job runs
- **THEN** the `DISCORD_TOKEN` secret SHALL NOT be passed to it as an environment variable or input

### Requirement: Announcement failure does not fail the pipeline
A failure to post the Discord announcement SHALL NOT mark the pipeline as failed, since the deploy and release have already succeeded.

#### Scenario: Discord webhook unreachable
- **WHEN** the curl POST to the Discord webhook returns a non-2xx status or times out
- **THEN** the announce step SHALL log the failure but the job SHALL exit successfully
