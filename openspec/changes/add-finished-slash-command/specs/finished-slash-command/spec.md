## ADDED Requirements

### Requirement: Register /finished slash command on startup
The system SHALL register a global Discord application command named `finished` with the description "Mark your scorecard as complete for today" on every bot startup. Registration SHALL be idempotent; re-registering an identical command definition SHALL NOT cause errors or duplicate commands.

#### Scenario: Bot starts up
- **WHEN** the bot application starts
- **THEN** a global slash command named `finished` is registered with Discord

### Requirement: Mark scoreboard complete via /finished
When a tracked user invokes `/finished`, the system SHALL set the `complete` flag to `true` on that user's `Scoreboard` for the current date (GMT) and persist the change.

#### Scenario: User marks their scoreboard complete
- **WHEN** a tracked user invokes `/finished`
- **THEN** the `complete` field on their Scoreboard for today's date is set to `true` and saved

#### Scenario: User invokes /finished a second time
- **WHEN** a tracked user invokes `/finished` when their Scoreboard for today is already `complete = true`
- **THEN** the system performs an idempotent update (no error) and responds to indicate it was already complete

### Requirement: Ephemeral confirmation reply
The system SHALL reply to every `/finished` invocation with an ephemeral Discord message (visible only to the invoking user) indicating the outcome.

#### Scenario: Successful completion
- **WHEN** a tracked user invokes `/finished` and has a Scoreboard for today
- **THEN** the bot replies with an ephemeral confirmation message indicating the scoreboard was marked complete

#### Scenario: Already complete
- **WHEN** a tracked user invokes `/finished` and their Scoreboard for today is already complete
- **THEN** the bot replies with an ephemeral message indicating it was already marked complete

#### Scenario: No scoreboard for today
- **WHEN** a tracked user invokes `/finished` but has no Scoreboard for today's date
- **THEN** the bot replies with an ephemeral message indicating no results have been submitted for today

#### Scenario: User is not tracked
- **WHEN** a Discord user who is not in the bot's configured user list invokes `/finished`
- **THEN** the bot replies with an ephemeral message indicating they are not a tracked user
