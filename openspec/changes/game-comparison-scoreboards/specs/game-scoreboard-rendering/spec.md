## ADDED Requirements

### Requirement: Shared scoreboard layout
The system SHALL render all game comparison scoreboards using a fixed 35-character-wide layout inside a Discord code block with the following structure: header line, separator, name/score row, separator, emoji grid rows, separator, result message, separator.

#### Scenario: Two-player scoreboard structure
- **WHEN** both players have submitted results for a game
- **THEN** the rendered scoreboard SHALL contain: a header with a single leading space, a 35-dash separator, a name/score row with the left player right-aligned to position 15 and the right player left-aligned from position 21 with the `|` at position 18, a separator, side-by-side emoji grid rows, a separator, a result message with a single leading space, and a final separator

#### Scenario: Single-player scoreboard structure
- **WHEN** only one player has submitted a result for a game
- **THEN** the rendered scoreboard SHALL display that player's data only with no centre `|` separator, no second column in the emoji grid, and a result message of `⏳ [Missing player] hasn't submitted`

#### Scenario: No results submitted
- **WHEN** neither player has submitted a result for a game
- **THEN** no scoreboard SHALL be rendered for that game

### Requirement: Column ordering by grid length
The system SHALL place the player whose emoji grid has more rows in the left column. If both players have the same number of rows (including all tie scenarios), column order SHALL follow the configured player order.

#### Scenario: Different row counts
- **WHEN** Player A has 6 emoji grid rows and Player B has 4 rows
- **THEN** Player A SHALL appear in the left column and Player B in the right column

#### Scenario: Same row counts
- **WHEN** both players have the same number of emoji grid rows
- **THEN** the player listed first in the channel configuration SHALL appear in the left column

### Requirement: Result messages
The system SHALL display result messages based on the outcome: `🤝 Tie!` for any tie, `🏆 [Winner] wins! (-N)` when both players completed and the winner has a better metric by N, `🏆 [Winner] wins!` when one player completed and the other failed, and `⏳ [Missing player] hasn't submitted` when only one result is present.

#### Scenario: Tie result
- **WHEN** both players complete with the same score metric
- **THEN** the result message SHALL be `🤝 Tie!`

#### Scenario: Win with differential
- **WHEN** both players completed and have different score metrics
- **THEN** the result message SHALL be `🏆 [Winner] wins! (-N)` where N is the absolute difference

#### Scenario: Win by completion
- **WHEN** one player completed and the other failed (X)
- **THEN** the result message SHALL be `🏆 [Winner] wins!` with no differential

#### Scenario: Waiting for second player
- **WHEN** only one player has submitted
- **THEN** the result message SHALL be `⏳ [Missing player] hasn't submitted`

### Requirement: Wordle scoreboard
The system SHALL render a Wordle comparison scoreboard with header `Wordle #[puzzle number]`, score field showing guess count (1–6) or `X` for failures, emoji grid rows of 5 emojis each with 4 leading spaces and a 5-space gap between columns, and winner determined by fewer guesses.

#### Scenario: Wordle win with both complete
- **WHEN** William guesses in 6 and Conor guesses in 4
- **THEN** William (more rows) SHALL be in the left column, Conor in the right, and result SHALL be `🏆 Conor wins! (-2)`

#### Scenario: Wordle tie with same guesses
- **WHEN** both players guess in 4
- **THEN** configured order SHALL determine columns and result SHALL be `🤝 Tie!`

#### Scenario: Wordle tie with both failed
- **WHEN** both players fail (X)
- **THEN** configured order SHALL determine columns and result SHALL be `🤝 Tie!`

#### Scenario: Wordle win with one failure
- **WHEN** one player completes in 4 guesses and the other fails (X)
- **THEN** the failing player (6 rows) SHALL be left, the winner right, and result SHALL be `🏆 [Winner] wins!` with no differential

#### Scenario: Wordle single submission
- **WHEN** only one player has submitted a Wordle result
- **THEN** a single-player layout SHALL be rendered with `⏳ [Other] hasn't submitted`

### Requirement: Connections scoreboard
The system SHALL render a Connections comparison scoreboard with header `Connections #[puzzle number]`, score field showing mistake count (0–N) or `X` for failures, emoji grid rows of 4 emojis each with 6 leading spaces and a 5-space gap between columns, and winner determined by fewer mistakes.

#### Scenario: Connections win with both complete
- **WHEN** William makes 2 mistakes and Conor makes 0
- **THEN** William (more rows) SHALL be left, Conor right, and result SHALL be `🏆 Conor wins! (-2)`

#### Scenario: Connections tie with same mistakes
- **WHEN** both players make 0 mistakes
- **THEN** configured order SHALL determine columns and result SHALL be `🤝 Tie!`

#### Scenario: Connections single submission
- **WHEN** only one player has submitted a Connections result
- **THEN** a single-player layout SHALL be rendered with `⏳ [Other] hasn't submitted`

### Requirement: Strands scoreboard
The system SHALL render a Strands comparison scoreboard with header `Strands #[puzzle number] - "[tagline]"`, score field showing hints used (0–N), emoji grid rows of up to 4 emojis each with 6 leading spaces and a 5-space gap between columns (with 2 extra spaces per missing emoji on shorter left-column rows to maintain right-column alignment), and winner determined first by fewer hints, then by earlier spangram position as tiebreaker.

#### Scenario: Strands win by hints
- **WHEN** William uses 2 hints and Conor uses 0 hints
- **THEN** William (more rows) SHALL be left, Conor right, and result SHALL be `🏆 Conor wins! (-2)`

#### Scenario: Strands win by spangram position
- **WHEN** both players use 0 hints but Player A finds spangram at position 1 and Player B at position 4
- **THEN** Player A wins and result SHALL be `🏆 [Player A] wins! (-3)`

#### Scenario: Strands tie
- **WHEN** both players use 0 hints and find spangram at the same position
- **THEN** result SHALL be `🤝 Tie!`

#### Scenario: Strands single submission
- **WHEN** only one player has submitted a Strands result
- **THEN** a single-player layout SHALL be rendered with `⏳ [Other] hasn't submitted`

### Requirement: Extensible scoreboard framework
The system SHALL provide a scoreboard rendering interface that allows new game types to be added by implementing a single game-specific class, without modifying the shared rendering pipeline or coordinator logic.

#### Scenario: Adding a new game type
- **WHEN** a developer creates a new class implementing the game scoreboard interface
- **THEN** the new game's comparison scoreboard SHALL automatically be included in the status channel output without changes to existing classes

### Requirement: Scoreboard integration with results channel
The system SHALL post comparison scoreboards as individual Discord messages (one per game) in a newly configured **results channel** (separate from the status channel) once both players have either submitted all games for the day or marked themselves finished via the `/finished` command. The status table continues to post to the existing status channel unchanged. Games where neither player submitted SHALL be skipped. Games where only one player submitted (because the other used `/finished` without submitting) SHALL produce a single-player scoreboard. The system SHALL track the Discord message ID of each posted scoreboard.

#### Scenario: Both players done — all games submitted
- **WHEN** both players have submitted all games for the day
- **THEN** the results channel SHALL receive one two-player comparison scoreboard message per game

#### Scenario: Both players done — one used /finished without submitting all games
- **WHEN** one player uses `/finished` without having submitted all games, and the other player is also done
- **THEN** the results channel SHALL receive scoreboard messages for each game, using a single-player layout for any game the `/finished` player did not submit

#### Scenario: Incomplete scoreboard replaced on late submission
- **WHEN** a single-player scoreboard has been posted to the results channel for a game AND the missing player subsequently submits a result for that game
- **THEN** the existing single-player scoreboard message SHALL be deleted and replaced with a new completed two-player scoreboard for that game in the results channel

### Requirement: Test coverage
All new scoreboard rendering classes, winner determination logic, and layout formatting SHALL have unit test coverage meeting or exceeding the project's 80% instruction and branch coverage threshold.

#### Scenario: Coverage verification
- **WHEN** `mvn verify -Dtest='!com.wandocorp.nytscorebot.SmokeTest'` is run
- **THEN** the JaCoCo coverage check SHALL pass with ≥80% instruction and branch coverage
