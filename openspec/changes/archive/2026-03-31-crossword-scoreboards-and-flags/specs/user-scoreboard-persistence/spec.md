## MODIFIED Requirements

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
