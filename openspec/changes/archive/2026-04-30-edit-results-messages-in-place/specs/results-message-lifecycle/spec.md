## ADDED Requirements

### Requirement: First post creates a new message
For every long-lived message slot (each scoreboard, the win-streak summary, the status board), the very first post of the slot's lifetime SHALL create a new Discord message and store its ID for later updates.

#### Scenario: Slot has no tracked message ID
- **WHEN** the bot needs to render content for a slot and no message ID is tracked for that slot
- **THEN** the bot SHALL call `MessageChannel.createMessage(...)` with the rendered content
- **AND** SHALL store the resulting message ID against the slot

### Requirement: Subsequent updates edit the existing message in place
When a slot already has a tracked message ID, the bot SHALL update the existing message via `Message.edit(...)` rather than deleting and reposting.

#### Scenario: Scoreboard refreshed after a flag change
- **WHEN** `ResultsChannelService.refreshGame(gameType)` is invoked and a message ID is already tracked for that game type
- **THEN** the bot SHALL edit the existing message in place
- **AND** SHALL NOT delete the previous message
- **AND** SHALL NOT post a new message
- **AND** the tracked message ID SHALL remain unchanged

#### Scenario: Status board refreshed on a new submission
- **WHEN** `StatusChannelService.refresh(contextMessage)` is invoked and a status board message ID is tracked
- **THEN** the bot SHALL edit the existing status board message in place with the new table
- **AND** SHALL NOT delete or repost the status board
- **AND** SHALL handle the contextMessage via the separate ephemeral notification path (see "Per-event notifications are posted as short-lived sibling messages")

#### Scenario: Win-streak summary refreshed after a duo flag change
- **WHEN** `ResultsChannelService.refreshGame("Main")` is invoked and a win-streak summary message ID is tracked
- **THEN** the bot SHALL edit the existing summary message in place

### Requirement: Edit failure falls back to a fresh post
If editing the tracked message fails (for example because the message was deleted out-of-band), the bot SHALL recover by posting a new message and replacing the tracked ID, so subsequent refreshes continue to function.

#### Scenario: Tracked message no longer exists
- **WHEN** the bot attempts `Message.edit(...)` on a tracked ID and Discord returns an error indicating the message is gone
- **THEN** the bot SHALL post a new message via `createMessage(...)` with the same content
- **AND** SHALL replace the tracked message ID with the new message's ID
- **AND** SHALL log the fallback at info or warn level

### Requirement: Update notifications are suppressed
Editing-in-place SHALL NOT trigger a Discord notification or push the message to the bottom of the channel; only the initial post of a slot triggers a notification.

#### Scenario: Status board edited multiple times during the day
- **WHEN** the status board is refreshed several times after its initial post
- **THEN** each refresh SHALL leave the message at its original channel position
- **AND** SHALL NOT generate per-update notifications

### Requirement: Slot tracking is per-bot-process
Tracked message IDs SHALL live in memory for the lifetime of the bot process. After a restart, the bot MAY treat any prior message as untracked and post a fresh message on the next refresh.

#### Scenario: Bot restarts mid-day
- **WHEN** the bot restarts and a refresh is triggered for a slot that had a message earlier in the day
- **THEN** the bot SHALL post a new message for that slot
- **AND** the previous message MAY remain in the channel untouched

### Requirement: Status board is a single persistent message
The status channel SHALL contain at most one bot-managed status board message at a time. The same message SHALL be reused across submissions, refreshes, and day boundaries; it SHALL NOT be deleted-and-reposted on update.

#### Scenario: Multiple submissions on the same day
- **WHEN** several submissions, `/finished`, or `/flag` events trigger `StatusChannelService.refresh(...)` during the same day
- **THEN** the bot SHALL edit the single tracked status board message for each refresh
- **AND** the channel SHALL contain exactly one bot-posted status board message after all refreshes complete

### Requirement: Status board contains only the table
The persistent status board message SHALL render only the status table (header row, separator, six game rows, footer row). It SHALL NOT include any per-event context message above or below the table.

#### Scenario: Status board rendered after a submission
- **WHEN** the status board is built or refreshed
- **THEN** its content SHALL begin with the table's code block opening
- **AND** SHALL NOT contain any "X submitted Y" or "X finished" preamble

### Requirement: Per-event notifications are posted as short-lived sibling messages
For every refresh that includes a context message (a submission, a `/finished` toggle, or a `/flag` change), the bot SHALL post that context message as its own message in the same channel and SHALL automatically delete it after a short delay (10 seconds by default), leaving the persistent status board untouched by this side message.

#### Scenario: Player submits a result
- **WHEN** a player's submission triggers `StatusChannelService.refresh("Conor submitted Wordle")`
- **THEN** the bot SHALL post a message containing "Conor submitted Wordle" in the status channel
- **AND** SHALL schedule deletion of that notification message approximately 10 seconds later
- **AND** SHALL edit the persistent status board in place to reflect the new state

#### Scenario: Notification delete fails
- **WHEN** the scheduled deletion of a notification message fails (e.g. the message was already removed)
- **THEN** the bot SHALL log the failure at warn level and otherwise ignore it
- **AND** SHALL NOT retry the deletion

### Requirement: Status board resets at midnight
At 00:00 in the configured puzzle timezone, the bot SHALL edit the persistent status board to its empty/base state for the new day (all submission cells showing the pending indicator, both players showing as not-finished). It SHALL NOT post a new message at midnight; the same persistent message SHALL be edited.

#### Scenario: Midnight tick with both players finished yesterday
- **WHEN** the clock reaches 00:00 and the status board shows yesterday's completed state
- **THEN** the bot SHALL edit the existing status board message in place
- **AND** the new content SHALL show every game cell as pending and both players as not-finished
- **AND** the channel SHALL still contain only one status board message

#### Scenario: Midnight tick with no tracked message
- **WHEN** the clock reaches 00:00 and no status board message ID is tracked
- **THEN** the bot SHALL skip the reset for that tick (the next refresh will post a fresh empty board)
- **AND** SHALL NOT post a redundant midnight message
