## ADDED Requirements

### Requirement: Midnight rollover steps run in a deterministic order

The midnight rollover SHALL execute its steps in a single, explicitly ordered sequence rather than as independent scheduled jobs whose relative order is undefined.

The order SHALL be:

1. Apply crossword win-streak forfeits for the closing date.
2. Force-post the closing date's results boards and win-streak summary, if not already posted.
3. Evaluate and post the triple crown for the closing date.
4. Reset the status board for the new day.

Each step SHALL be independently guarded so that a failure in one step does not prevent the remaining steps from running.

#### Scenario: Forfeits are applied before boards are rendered
- **WHEN** the midnight rollover runs for a closing date
- **THEN** `WinStreakService.applyForfeit` SHALL have completed for Mini, Midi, and Main before the results boards are rendered
- **AND** the rendered win-streak summary SHALL reflect the finalized post-forfeit values

#### Scenario: Crown is evaluated after boards are posted
- **WHEN** the midnight rollover posts the closing date's boards
- **THEN** triple crown evaluation SHALL occur after the board and summary writes have been issued

#### Scenario: A failing step does not suppress later steps
- **WHEN** the force-post step throws during the midnight rollover
- **THEN** the bot SHALL log the error
- **AND** SHALL still evaluate the triple crown and reset the status board

#### Scenario: Forfeit application fails
- **WHEN** the forfeit step throws during the midnight rollover
- **THEN** the bot SHALL log the error
- **AND** SHALL still force-post the closing date's boards and reset the status board

### Requirement: Win-streak players are resolved by immutable Discord user ID

The midnight rollover SHALL resolve both players via `app_user.discord_user_id` rather than by display name, so that a change to a configured display name cannot cause the rollover to silently skip a player.

#### Scenario: Configured display name has changed since the user row was created
- **GIVEN** a player's `app_user.name` no longer matches the configured `discord.channels[n].name`
- **WHEN** the midnight rollover resolves players
- **THEN** the bot SHALL resolve the player by `discord_user_id` and process their forfeits normally

#### Scenario: A player is not yet registered
- **WHEN** the midnight rollover cannot resolve a player by `discord_user_id`
- **THEN** the bot SHALL log at debug level that the rollover was skipped pending registration
- **AND** SHALL NOT apply forfeits for that date
