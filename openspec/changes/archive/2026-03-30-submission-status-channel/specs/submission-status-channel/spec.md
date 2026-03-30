## ADDED Requirements

### Requirement: Status channel configuration
The system SHALL support an optional top-level `discord.statusChannelId` property in `application.yml`. When this property is absent, the submission status feature SHALL be disabled and all related behaviour is a no-op.

#### Scenario: Status channel configured
- **WHEN** `discord.statusChannelId` is set to a valid Discord channel ID
- **THEN** the bot activates submission status tracking for that channel

#### Scenario: Status channel not configured
- **WHEN** `discord.statusChannelId` is absent from the application config
- **THEN** no status messages are posted and no messages are deleted from any channel

### Requirement: Auto-refreshed status message
After any successful game result submission or `/finished` command, the system SHALL delete its most recent status message in the status channel (if one exists) and post a new one reflecting the updated state.

#### Scenario: First submission of the day
- **WHEN** a player submits a valid result and no status message exists yet
- **THEN** the bot posts a new status message in the status channel

#### Scenario: Subsequent submission
- **WHEN** a player submits a valid result and a previous status message already exists
- **THEN** the bot deletes the previous status message and posts a new one

#### Scenario: Delete fails with 404
- **WHEN** the bot attempts to delete a previous status message that no longer exists on Discord
- **THEN** the error is swallowed and a new status message is posted regardless

#### Scenario: Rejected submission does not refresh
- **WHEN** a game result submission is rejected (e.g., wrong puzzle number, duplicate)
- **THEN** the status message is NOT updated

### Requirement: Status table content
The status message SHALL contain a formatted table listing every tracked player as a row, with a column for each game type (Wordle, Connections, Strands, Mini Crossword, Midi Crossword, Daily Crossword) and a final **Status** column summarising the player's overall progress for the day.

Per-game cells SHALL use ✅ for submitted and ⏳ for not yet submitted.

The Status column SHALL display one of the following plain-text labels, evaluated in priority order:
1. **Complete!** — player has submitted all six game types today
2. **Finished** — player has invoked `/finished` (`complete = true`), regardless of how many games are submitted
3. **In Progress** — player has submitted at least one game but not all six, and has not marked finished
4. **No Submissions** — player has submitted no games today

#### Scenario: Player has marked finished
- **WHEN** a player's scoreboard has `complete = true`
- **THEN** their Status column shows `Finished`, regardless of how many games they have submitted

#### Scenario: Player has submitted all games and not marked finished
- **WHEN** a player has results for all six game types today and `complete = false`
- **THEN** their Status column shows `Complete`

#### Scenario: Player has submitted some games and not marked finished
- **WHEN** a player has results for at least one but fewer than six game types today and `complete = false`
- **THEN** their Status column shows `In Progress`

#### Scenario: Player has no submissions
- **WHEN** a tracked player has submitted no results today and `complete = false`
- **THEN** their Status column shows `No Submissions` and all game cells display ⏳

#### Scenario: Per-game cell for submitted game
- **WHEN** a player has a saved result for a given game today
- **THEN** the corresponding cell displays ✅

#### Scenario: Per-game cell for unsubmitted game
- **WHEN** a player has no saved result for a given game today
- **THEN** the corresponding cell displays ⏳

### Requirement: Status channel is read-only for non-bot users
The system SHALL delete any message posted to the status channel by a user other than the bot itself.

#### Scenario: Bot posts in status channel
- **WHEN** the bot itself posts a message in the status channel
- **THEN** the message is not deleted

### Requirement: constants class
All emoji string literals, messages, and status indicators used in bot replies and the status table SHALL be defined as `public static final String` constants in a single `BotText` class. No emoji literals, etc. SHALL appear outside this class.

#### Scenario: Constant used in rejection reply
- **WHEN** the bot sends a rejection reply
- **THEN** any text in the message is sourced from `Bottext` constants

#### Scenario: Constant used in status table
- **WHEN** the bot builds the status table
- **THEN** all constants are sourced from `BotText` constants
