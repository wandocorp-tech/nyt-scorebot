## ADDED Requirements

### Requirement: User entity per person
The system SHALL maintain a `User` record for each configured person. A `User` SHALL be identified by the channel ID and SHALL store the person's display name. If no `User` exists for a channel ID when a result is received, the system SHALL create one automatically.

#### Scenario: First result for a person
- **WHEN** a game result is successfully parsed from a channel that has no existing `User` record
- **THEN** a new `User` record is created with the channel ID and configured person name

#### Scenario: Subsequent result for existing person
- **WHEN** a game result is successfully parsed from a channel that already has a `User` record
- **THEN** no duplicate `User` record is created; the existing record is reused

### Requirement: Scoreboard structure with date and per-game result fields
A `Scoreboard` record SHALL represent a single calendar date's results for a person. It SHALL contain:
- A `date` field (`LocalDate`) identifying which day the results belong to
- One nullable field for each supported game type: `wordleResult`, `connectionsResult`, `strandsResult`, `miniCrosswordResult`, `midiCrosswordResult`, and `dailyCrosswordResult` — where `miniCrosswordResult` and `midiCrosswordResult` are typed as `CrosswordResult` keyed by `CrosswordType` (MINI, MIDI respectively), and `dailyCrosswordResult` is typed as `MainCrosswordResult` (extending `CrosswordResult` with flag fields) keyed by `CrosswordType` MAIN

Only the field(s) corresponding to games played on that date will be populated; all others SHALL be null. A `complete` boolean flag (default `false`) SHALL indicate whether the user has declared they are finished submitting results for that day.

#### Scenario: Wordle result received
- **WHEN** a `WordleResult` is parsed from a person's channel
- **THEN** a `Scoreboard` for that date is created (or updated) with the `wordleResult` field populated and the date set to the puzzle date

#### Scenario: Multiple games on same date
- **WHEN** both a `WordleResult` and a `ConnectionsResult` are received for the same person on the same date
- **THEN** both fields are populated on the single `Scoreboard` record for that date

#### Scenario: Main crossword result stored as MainCrosswordResult
- **WHEN** a main crossword result is parsed from a person's channel
- **THEN** the `dailyCrosswordResult` field is populated as a `MainCrosswordResult` instance with flag fields defaulting to null

#### Scenario: Scoreboard defaults to incomplete
- **WHEN** a `Scoreboard` record is first created
- **THEN** the `complete` flag is `false`

#### Scenario: Failed parse does not persist
- **WHEN** a message cannot be parsed as a known game result
- **THEN** no `Scoreboard` record is created or modified

### Requirement: User owns a list of scoreboards
The system SHALL model the relationship between `User` and `Scoreboard` as one-to-many: each `User` SHALL have an ordered list of `Scoreboard` entries, with at most one entry per calendar date.

#### Scenario: Multiple game results on the same day
- **WHEN** a person's channel receives three valid game results on the same calendar date
- **THEN** the `User` record is associated with exactly one `Scoreboard` entry for that date, with each result stored in its respective field

#### Scenario: Results on different days
- **WHEN** a person's channel receives game results on two different calendar dates
- **THEN** the `User` record is associated with two `Scoreboard` entries, one per date

### Requirement: Persistent storage across restarts
The system SHALL use a file-based H2 database so that `User` and `Scoreboard` records survive application restarts.

#### Scenario: Data survives restart
- **WHEN** the application is stopped and restarted
- **THEN** previously saved `User` and `Scoreboard` records are still present and queryable
