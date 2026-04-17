## MODIFIED Requirements

### Requirement: Scoreboard structure with date and per-game result fields
A `Scoreboard` record SHALL represent a single calendar date's results for a person. It SHALL contain:
- A `date` field (`LocalDate`) identifying which day the results belong to
- A `finished` boolean flag (default `false`) indicating whether the user has declared they are finished submitting results for that day
- A one-to-many relationship to `GameResultEntity` records, each representing a single game result for that day

Game results SHALL be stored in a separate `game_result` table using JPA `@Inheritance(SINGLE_TABLE)` with a `game_type` discriminator column. Each `GameResultEntity` subclass corresponds to a `GameType` enum value. The wide-table embedding with `@Embedded`/`@AttributeOverride` SHALL be removed.

#### Scenario: Wordle result received
- **WHEN** a `WordleResult` is parsed from a person's channel
- **THEN** a `Scoreboard` for that date is created (or updated) and a `WordleResultEntity` row is inserted in the `game_result` table linked to that Scoreboard

#### Scenario: Multiple games on same date
- **WHEN** both a `WordleResult` and a `ConnectionsResult` are received for the same person on the same date
- **THEN** both are stored as separate rows in `game_result`, both linked to the single `Scoreboard` record for that date

#### Scenario: Scoreboard defaults to incomplete
- **WHEN** a `Scoreboard` record is first created
- **THEN** the `finished` flag is `false`

#### Scenario: Failed parse does not persist
- **WHEN** a message cannot be parsed as a known game result
- **THEN** no `Scoreboard` record is created or modified

### Requirement: Connections solve-order serialisation
The system SHALL serialise `ConnectionsResult.solveOrder` as a comma-separated string in the database. When deserialising, an empty or blank database value SHALL produce an empty list, not a list containing an empty string.

#### Scenario: Solve order with entries round-trips correctly
- **WHEN** a `ConnectionsResult` with a non-empty solve order is saved and reloaded
- **THEN** the solve order list matches the original

#### Scenario: Delimiter collision is handled safely
- **WHEN** a solve-order entry contains a comma character
- **THEN** the serialization scheme SHALL either escape or encode the value such that a round-trip preserves the original list without corruption

### Requirement: Duplicate result detection uses GameType
Duplicate detection SHALL query the `game_result` table for an existing row matching the Scoreboard ID and `game_type` discriminator, rather than checking nullable embedded fields on the Scoreboard entity.

#### Scenario: Duplicate Wordle submission rejected
- **WHEN** a WordleResult is submitted for a user on a date that already has a WordleResultEntity
- **THEN** the submission is rejected with a duplicate outcome

#### Scenario: Different game on same day accepted
- **WHEN** a ConnectionsResult is submitted for a user on a date that already has a WordleResultEntity but no ConnectionsResultEntity
- **THEN** the submission is accepted
