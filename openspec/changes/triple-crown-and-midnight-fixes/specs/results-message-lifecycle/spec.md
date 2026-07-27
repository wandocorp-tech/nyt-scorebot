## MODIFIED Requirements

### Requirement: Subsequent updates edit the existing message in place
When a slot already has a tracked message ID, the bot SHALL update the existing message via `Message.edit(...)` rather than deleting and reposting.

This applies to every unconditional slot — the game scoreboards, the win-streak summary, and the status board. It SHALL NOT apply to conditional slots, whose content may cease to be applicable and which are governed by "Conditional slots are deleted when their content no longer applies".

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

#### Scenario: Conditional slot is exempt
- **WHEN** a slot is declared conditional and its content no longer applies
- **THEN** the bot SHALL delete the message rather than editing it to a placeholder or revocation notice

## ADDED Requirements

### Requirement: Conditional slots are deleted when their content no longer applies

A conditional slot is one whose message is only appropriate while a condition holds. When the condition ceases to hold, the bot SHALL delete the tracked message and clear the tracked ID, rather than editing it in place.

Deletion SHALL be serialized through the same per-slot write chain used for posts and edits, so a delete cannot overtake an in-flight post and leave an orphaned message in the channel. Deleting a message that no longer exists SHALL be tolerated and logged, not propagated as an error.

#### Scenario: Condition ceases to hold
- **GIVEN** a conditional slot has a tracked message ID
- **WHEN** a refresh determines the condition no longer holds
- **THEN** the bot SHALL delete the tracked message
- **AND** SHALL clear the tracked message ID for that slot
- **AND** SHALL NOT post a replacement message

#### Scenario: Delete is issued while a post for the same slot is in flight
- **GIVEN** a post for a conditional slot has been issued but has not yet returned a message ID
- **WHEN** a delete for the same slot is requested
- **THEN** the delete SHALL be chained after the in-flight post completes
- **AND** the posted message SHALL be deleted rather than orphaned

#### Scenario: Condition holds again after deletion
- **GIVEN** a conditional slot's message was deleted and its ID cleared
- **WHEN** a later refresh determines the condition holds once more
- **THEN** the bot SHALL post a new message and track its ID

#### Scenario: Tracked message already removed in Discord
- **WHEN** the bot attempts to delete a tracked message that no longer exists
- **THEN** the bot SHALL clear the tracked ID
- **AND** SHALL log the condition at warn level
- **AND** SHALL NOT propagate an error to the caller

### Requirement: Scoreboards are resolved by immutable Discord user ID

When assembling the render context for a results refresh, the bot SHALL associate each configured channel with its player's scoreboard using the immutable `app_user.discord_user_id`, and SHALL NOT use the mutable display name as a lookup key.

Display names remain in use for rendering output only.

#### Scenario: Display name in configuration differs from the stored name
- **GIVEN** a player's `app_user.name` was persisted from an earlier value of `discord.channels[n].name`
- **AND** the configured display name has since changed
- **WHEN** the bot assembles the render context for a date
- **THEN** the bot SHALL still resolve that player's scoreboard via `discord_user_id`
- **AND** SHALL render the board using the currently configured display name

#### Scenario: Stored Discord user ID is missing
- **GIVEN** an `app_user` row has a null `discord_user_id`
- **WHEN** the bot attempts to resolve that player's scoreboard
- **THEN** the bot SHALL log the condition at warn level
- **AND** MAY fall back to resolving by display name for that player

### Requirement: Every early return in the results path is logged

Each guard that causes a results refresh to produce no Discord write SHALL emit a log line identifying which guard tripped and the values that caused it, so that a silent no-op is distinguishable in the service log from a successful write or a Discord failure.

#### Scenario: Results channel is not configured
- **WHEN** a refresh is requested and no results channel ID is configured
- **THEN** the bot SHALL log at debug level that the refresh was skipped for that reason

#### Scenario: Both-finished gate blocks a non-forced refresh
- **WHEN** a non-forced refresh is requested and not every player has finished
- **THEN** the bot SHALL log at debug level that the refresh was skipped pending completion

#### Scenario: Fewer than two channels are configured
- **WHEN** a refresh is requested and fewer than two channels are configured
- **THEN** the bot SHALL log at warn level that the refresh was skipped for that reason

#### Scenario: Scoreboards exist for the date but nothing renders
- **WHEN** a refresh finds one or more scoreboards for the date but every game renders empty
- **THEN** the bot SHALL log at warn level, including the date and the number of scoreboards found
- **AND** the log line SHALL be emitted before the refresh returns

#### Scenario: Midnight force-post is skipped as already posted
- **WHEN** the midnight rollover skips force-posting because results are already recorded as posted for that date
- **THEN** the bot SHALL log at info level that the force-post was skipped and for which date
