## MODIFIED Requirements

### Requirement: Announcement message format
The Discord announcement message SHALL be a plain-markdown message containing the version header and a concise bullet list of changes. It SHALL NOT include a link to the GitHub Release.

#### Scenario: Message structure
- **WHEN** the announce job posts to Discord
- **THEN** the message SHALL begin with `🚀 nyt-scorebot v{N} deployed!`, followed by the commit-log bullet list, and SHALL NOT contain a `📋 Full release notes →` footer link

#### Scenario: Message stays within Discord size limit
- **WHEN** the announce job posts to Discord
- **THEN** the total message length SHALL be ≤ 2000 characters; if the commit log would exceed this, it SHALL be truncated with an ellipsis before posting; the truncation overhead calculation SHALL account for only the header and separators (not a footer)

## REMOVED Requirements

### Requirement: release_url input parameter
**Reason**: The footer link is removed, so the `release_url` input is no longer needed.
**Migration**: Remove `release_url` from the `announce` workflow's `inputs` block and from all callers in `pipeline.yml`.

#### Scenario: No release URL input
- **WHEN** the announce workflow is called from the pipeline
- **THEN** it SHALL NOT accept or use a `release_url` input parameter
