## MODIFIED Requirements

### Requirement: CrosswordResult type hierarchy
The crossword result model SHALL be split into two classes:
- `TimedCrosswordResult`: Base class for crosswords with time-based scoring. Contains fields: `totalSeconds`, `date`, `crosswordType`, `puzzleNumber`, and shared crossword fields. Used for Mini and Midi crosswords.
- `MainCrosswordResult extends TimedCrosswordResult`: Adds `duo` (Boolean), `lookups` (Integer), `checkUsed` (Boolean) fields specific to the Main/Daily crossword.

Fields `duo`, `lookups`, and `checkUsed` SHALL NOT exist on `TimedCrosswordResult`. They SHALL only be present on `MainCrosswordResult`.

#### Scenario: Mini crossword result has no main-specific fields
- **WHEN** a Mini crossword result is parsed
- **THEN** the result is a `TimedCrosswordResult` with `crosswordType = MINI` and no `duo`, `lookups`, or `checkUsed` fields

#### Scenario: Main crossword result has flag fields
- **WHEN** a Main crossword result is parsed
- **THEN** the result is a `MainCrosswordResult` with `crosswordType = MAIN` and `duo`, `lookups`, `checkUsed` fields available

#### Scenario: Midi crossword result has no main-specific fields
- **WHEN** a Midi crossword result is parsed
- **THEN** the result is a `TimedCrosswordResult` with `crosswordType = MIDI` and no `duo`, `lookups`, or `checkUsed` fields

### Requirement: Mini crossword daily comparison scoreboard
The system SHALL render a daily comparison scoreboard for the Mini crossword in the results channel. The scoreboard SHALL display each player's solve time (in MM:SS format) side-by-side, with an outcome row showing win/tie/unsubmitted status. The faster solve time (lower `totalSeconds`) SHALL win. The differential SHALL be expressed in seconds.

#### Scenario: Both players submitted Mini crossword
- **WHEN** both players have a Mini crossword result on today's scoreboard and both are marked finished
- **THEN** the results channel displays a Mini crossword scoreboard with both players' times and a win/tie outcome

#### Scenario: One player submitted Mini crossword
- **WHEN** only one player has a Mini crossword result on today's scoreboard
- **THEN** the results channel displays that player's time and a waiting indicator for the other player

### Requirement: Main crossword daily comparison scoreboard
The system SHALL render a daily comparison scoreboard for the Main crossword. The scoreboard SHALL display solve times and flag indicators (duo, lookups, check used) for each player.

#### Scenario: Both players submitted Main crossword with flags
- **WHEN** both players have a `MainCrosswordResult` and both are marked finished
- **THEN** the results channel displays times, flag indicators, and a win/tie outcome
