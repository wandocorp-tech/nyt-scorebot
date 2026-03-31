## ADDED Requirements

### Requirement: Mini crossword daily comparison scoreboard
The system SHALL render a daily comparison scoreboard for the Mini crossword in the results channel. The scoreboard SHALL display each player's solve time (in MM:SS format) side-by-side, with an outcome row showing win/tie/unsubmitted status. The faster solve time (lower `totalSeconds`) SHALL win. The differential SHALL be expressed in seconds.

#### Scenario: Both players submitted Mini crossword
- **WHEN** both players have a `miniCrosswordResult` on today's scoreboard and both are marked finished
- **THEN** the results channel displays a Mini crossword scoreboard with both players' times and a win/tie outcome (e.g., "🏆 Alice wins! (-48)" or "🤝 Tie!")

#### Scenario: One player submitted Mini crossword
- **WHEN** only one player has a `miniCrosswordResult` on today's scoreboard
- **THEN** the results channel displays that player's time and "⏳ [OtherPlayer] hasn't submitted"

#### Scenario: Neither player submitted Mini crossword
- **WHEN** neither player has a `miniCrosswordResult` on today's scoreboard
- **THEN** no Mini crossword scoreboard is rendered in the results channel

### Requirement: Midi crossword daily comparison scoreboard
The system SHALL render a daily comparison scoreboard for the Midi crossword in the results channel. The scoreboard SHALL display each player's solve time side-by-side, with an outcome row showing win/tie/unsubmitted status. The faster solve time SHALL win.

#### Scenario: Both players submitted Midi crossword
- **WHEN** both players have a `midiCrosswordResult` on today's scoreboard and both are marked finished
- **THEN** the results channel displays a Midi crossword scoreboard with both players' times and a win/tie outcome

#### Scenario: One player submitted Midi crossword
- **WHEN** only one player has a `midiCrosswordResult` on today's scoreboard
- **THEN** the results channel displays that player's time and a waiting indicator for the other player

#### Scenario: Neither player submitted Midi crossword
- **WHEN** neither player has a `midiCrosswordResult` on today's scoreboard
- **THEN** no Midi crossword scoreboard is rendered

### Requirement: Main crossword daily comparison scoreboard
The system SHALL render a daily comparison scoreboard for the Main crossword in the results channel. The scoreboard SHALL display each player's solve time side-by-side, with an outcome row showing win/tie/unsubmitted status. The faster solve time SHALL win. Additionally, if either player has flags set (duo, lookups, check), the scoreboard SHALL display a flags row beneath the solve times showing the applicable flag indicators for each player.

#### Scenario: Both players submitted Main crossword with no flags
- **WHEN** both players have a `dailyCrosswordResult` on today's scoreboard with no flags set
- **THEN** the results channel displays a Main crossword scoreboard with both players' times and a win/tie outcome, with no flags row

#### Scenario: Both players submitted Main crossword with flags
- **WHEN** both players have a `dailyCrosswordResult` and at least one player has flags set
- **THEN** the results channel displays the scoreboard with a flags row beneath the times, showing each player's flag indicators side-by-side (👫 for duo, 🔍×N for lookups, ✓ for check)

#### Scenario: One player submitted Main crossword
- **WHEN** only one player has a `dailyCrosswordResult` on today's scoreboard
- **THEN** the results channel displays that player's time (and flags if set) with a waiting indicator for the other player

#### Scenario: Neither player submitted Main crossword
- **WHEN** neither player has a `dailyCrosswordResult` on today's scoreboard
- **THEN** no Main crossword scoreboard is rendered

### Requirement: Crossword scoreboard ordering
The three crossword scoreboards SHALL appear in the results channel after the existing game scoreboards (Wordle, Connections, Strands) in the order: Mini, Midi, Main.

#### Scenario: All game scoreboards rendered
- **WHEN** both players have submitted results for all game types
- **THEN** the results channel displays scoreboards in order: Wordle, Connections, Strands, Mini Crossword, Midi Crossword, Main Crossword

### Requirement: Crossword scoreboard header uses date
The crossword scoreboard headers SHALL display the crossword date rather than a puzzle number, since crosswords use dates instead of sequential puzzle numbers. The format SHALL be the crossword date (e.g., "Mini Crossword - 3/31/2026").

#### Scenario: Crossword header displays date
- **WHEN** a crossword scoreboard is rendered for a result with `crosswordDate` of 2026-03-31
- **THEN** the header reads "[Type] Crossword - 3/31/2026" (e.g., "Mini Crossword - 3/31/2026")
