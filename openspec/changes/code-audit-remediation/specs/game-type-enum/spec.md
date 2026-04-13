## ADDED Requirements

### Requirement: GameType enum definition
The `nyt-scorebot-domain` module SHALL define a `GameType` enum with values: `WORDLE`, `CONNECTIONS`, `STRANDS`, `MINI_CROSSWORD`, `MIDI_CROSSWORD`, `MAIN_CROSSWORD`. Each value SHALL have a `label()` method returning the human-readable game name (e.g., "Wordle", "Mini Crossword").

#### Scenario: All supported game types are enumerated
- **WHEN** `GameType.values()` is called
- **THEN** exactly six values are returned: WORDLE, CONNECTIONS, STRANDS, MINI_CROSSWORD, MIDI_CROSSWORD, MAIN_CROSSWORD

#### Scenario: Label returns human-readable name
- **WHEN** `GameType.WORDLE.label()` is called
- **THEN** the result is `"Wordle"`

### Requirement: GameResult subclasses return their GameType
Each `GameResult` subclass SHALL implement an abstract `gameType()` method that returns the corresponding `GameType` enum value.

#### Scenario: WordleResult returns WORDLE
- **WHEN** `wordleResult.gameType()` is called
- **THEN** the result is `GameType.WORDLE`

#### Scenario: MainCrosswordResult returns MAIN_CROSSWORD
- **WHEN** `mainCrosswordResult.gameType()` is called
- **THEN** the result is `GameType.MAIN_CROSSWORD`

### Requirement: Streak entity uses GameType enum
The `Streak` entity SHALL store `gameType` as a `GameType` enum value using `@Enumerated(EnumType.STRING)`, replacing the raw String field. The database column SHALL contain the enum name (e.g., `"WORDLE"`).

#### Scenario: Streak persisted with enum game type
- **WHEN** a Streak with `gameType = GameType.WORDLE` is saved
- **THEN** the database column contains the string `"WORDLE"`

#### Scenario: Invalid game type string rejected
- **WHEN** the database contains a `game_type` value that does not match any `GameType` enum constant
- **THEN** loading the Streak throws an exception (JPA enumeration mapping failure)

### Requirement: GameResult polymorphic behavior methods
Each `GameResult` subclass SHALL implement the following abstract methods, eliminating instanceof checks in callers:
- `gameType()` — returns the `GameType` enum value
- `gameLabel()` — returns the display label for Discord messages
- `isSuccess()` — returns whether the result represents a successful game outcome
- `validate(PuzzleCalendar)` — validates the puzzle number against today's expected number, returning a validation result

#### Scenario: WordleResult.isSuccess returns true for solved puzzle
- **WHEN** `isSuccess()` is called on a WordleResult with `completed = true`
- **THEN** the result is `true`

#### Scenario: WordleResult.isSuccess returns false for failed puzzle
- **WHEN** `isSuccess()` is called on a WordleResult with `completed = false`
- **THEN** the result is `false`

#### Scenario: GameResult.validate checks puzzle number
- **WHEN** `validate(puzzleCalendar)` is called on a WordleResult with puzzleNumber 1234
- **AND** the puzzle calendar expects puzzle number 1234 for today
- **THEN** the validation result indicates success

#### Scenario: GameResult.validate rejects wrong puzzle number
- **WHEN** `validate(puzzleCalendar)` is called on a WordleResult with puzzleNumber 1233
- **AND** the puzzle calendar expects puzzle number 1234 for today
- **THEN** the validation result indicates a puzzle number mismatch
