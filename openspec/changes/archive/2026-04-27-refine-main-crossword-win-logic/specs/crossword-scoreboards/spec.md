## MODIFIED Requirements

### Requirement: Mini crossword daily comparison scoreboard
The system SHALL render a daily comparison scoreboard for the Mini crossword in the results channel. The scoreboard SHALL display each player's solve time (in MM:SS format) side-by-side, with an outcome row showing win/tie/unsubmitted status. The faster solve time (lower `totalSeconds`) SHALL win. The time differential SHALL be expressed in MM:SS format without a leading minus sign (e.g., `(0:48)`). When both players have identical `totalSeconds`, the outcome SHALL be a "Nuke!" (rendered with a distinct ☢️ marker) instead of the generic tie message.

#### Scenario: Both players submitted Mini crossword with different times
- **WHEN** both players have a `miniCrosswordResult` on today's scoreboard with different `totalSeconds`
- **THEN** the results channel displays a Mini crossword scoreboard with both players' times and a win outcome (e.g., "🏆 Alice wins! (0:48)")

#### Scenario: Both players submitted Mini crossword with the same time
- **WHEN** both players have a `miniCrosswordResult` on today's scoreboard with identical `totalSeconds`
- **THEN** the outcome row displays "☢️ Nuke!" rather than the generic "🤝 Tie!" message

#### Scenario: One player submitted Mini crossword
- **WHEN** only one player has a `miniCrosswordResult` on today's scoreboard
- **THEN** the results channel displays that player's time and "⏳ [OtherPlayer] hasn't submitted"

#### Scenario: Neither player submitted Mini crossword
- **WHEN** neither player has a `miniCrosswordResult` on today's scoreboard
- **THEN** no Mini crossword scoreboard is rendered in the results channel

### Requirement: Midi crossword daily comparison scoreboard
The system SHALL render a daily comparison scoreboard for the Midi crossword in the results channel. The scoreboard SHALL display each player's solve time side-by-side, with an outcome row showing win/tie/unsubmitted status. The faster solve time SHALL win. The time differential SHALL be expressed in MM:SS format without a leading minus sign. When both players have identical `totalSeconds`, the outcome SHALL be a "Nuke!" (☢️) instead of the generic tie message.

#### Scenario: Both players submitted Midi crossword with different times
- **WHEN** both players have a `midiCrosswordResult` on today's scoreboard with different `totalSeconds`
- **THEN** the results channel displays a Midi crossword scoreboard with both players' times and a win outcome (e.g., "🏆 Alice wins! (1:30)")

#### Scenario: Both players submitted Midi crossword with the same time
- **WHEN** both players have a `midiCrosswordResult` on today's scoreboard with identical `totalSeconds`
- **THEN** the outcome row displays "☢️ Nuke!"

#### Scenario: One player submitted Midi crossword
- **WHEN** only one player has a `midiCrosswordResult` on today's scoreboard
- **THEN** the results channel displays that player's time and a waiting indicator for the other player

#### Scenario: Neither player submitted Midi crossword
- **WHEN** neither player has a `midiCrosswordResult` on today's scoreboard
- **THEN** no Midi crossword scoreboard is rendered

### Requirement: Main crossword daily comparison scoreboard
The system SHALL render a daily comparison scoreboard for the Main crossword in the results channel. The scoreboard SHALL display each player's solve time side-by-side, with an outcome row showing the winner, a draw, or a "Nuke!" (☢️). Additionally, if either player has flags set (duo, lookups, check), the scoreboard SHALL display a flags row beneath the solve times showing the applicable flag indicators for each player.

The win/draw/Nuke outcome SHALL be determined by the `checkUsed` and `lookups` flags on each player's `MainCrosswordResult`, NOT by `totalSeconds` alone. A player is considered to have "used assistance" when `checkUsed` is true OR `lookups` is greater than zero. The `duo` flag SHALL NOT influence the outcome.

The decision rules SHALL be:
1. If both players used assistance, the outcome is a generic tie ("🤝 Tie!"), regardless of time.
2. If exactly one player used assistance, the other player wins, regardless of time, and the win SHALL be displayed without a time differential (i.e., "🏆 [Winner] wins!").
3. If neither player used assistance and their times differ, the lower time wins with the time differential displayed in MM:SS format without a leading minus sign (e.g., "🏆 [Winner] wins! (0:12)").
4. If neither player used assistance and their times are identical, the outcome SHALL be "☢️ Nuke!".

#### Scenario: Both players submitted Main crossword with no assistance, different times
- **WHEN** both players have a `mainCrosswordResult` with `checkUsed = false`/null and `lookups = 0`/null and different `totalSeconds`
- **THEN** the player with the lower `totalSeconds` wins, displayed as "🏆 [Winner] wins! (M:SS)" (e.g., "🏆 Alice wins! (0:12)")

#### Scenario: Both players submitted Main crossword with no assistance, identical times
- **WHEN** both players have a `mainCrosswordResult` with no assistance flags set and identical `totalSeconds`
- **THEN** the outcome row displays "☢️ Nuke!" (not "🤝 Tie!")

#### Scenario: Only one player used a check on Main crossword
- **WHEN** player A's `mainCrosswordResult` has `checkUsed = true` (or `lookups > 0`) and player B's has neither
- **THEN** player B wins regardless of `totalSeconds`, displayed as "🏆 [B] wins!" with no time differential

#### Scenario: Only one player used lookups on Main crossword
- **WHEN** player A's `mainCrosswordResult` has `lookups = 3` and player B's has `lookups = 0`/null and `checkUsed = false`/null
- **THEN** player B wins regardless of `totalSeconds`, displayed as "🏆 [B] wins!" with no time differential

#### Scenario: Both players used assistance on Main crossword
- **WHEN** both players' `mainCrosswordResult` has `checkUsed = true` or `lookups > 0` (in any combination)
- **THEN** the outcome row displays "🤝 Tie!" regardless of `totalSeconds`

#### Scenario: Duo flag does not affect Main crossword outcome determination
- **WHEN** one or both players have only the `duo` flag set (no `checkUsed`, no `lookups`) and times differ
- **THEN** the outcome is determined purely by `totalSeconds` as if no flags were set

#### Scenario: Winning player used duo on Main crossword (time-based win)
- **WHEN** neither player used assistance, player A has a lower `totalSeconds`, and player A has `duo = true`
- **THEN** the win message displays "🏆 [A] et al. wins! (M:SS)" instead of "🏆 [A] wins! (M:SS)"

#### Scenario: Winning player used duo on Main crossword (assisted-player disqualified)
- **WHEN** player B used assistance (check or lookups) and player A did not, and player A has `duo = true`
- **THEN** the win message displays "🏆 [A] et al. wins!" (no differential)

#### Scenario: Both players submitted Main crossword with flags row
- **WHEN** both players have a `mainCrosswordResult` and at least one player has flags set
- **THEN** the results channel displays the scoreboard with a flags row beneath the times, showing each player's flag indicators side-by-side (👫 for duo, 🔍×N for lookups, ✓ for check)

#### Scenario: One player submitted Main crossword
- **WHEN** only one player has a `mainCrosswordResult` on today's scoreboard
- **THEN** the results channel displays that player's time (and flags if set) with a waiting indicator for the other player

#### Scenario: Neither player submitted Main crossword
- **WHEN** neither player has a `mainCrosswordResult` on today's scoreboard
- **THEN** no Main crossword scoreboard is rendered
