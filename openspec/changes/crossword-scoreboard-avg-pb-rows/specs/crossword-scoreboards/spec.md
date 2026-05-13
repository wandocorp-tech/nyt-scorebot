## ADDED Requirements

### Requirement: Daily crossword scoreboards display all-time avg and pb rows
Each daily crossword scoreboard (Mini, Midi, Main) SHALL render two additional rows beneath the outcome row:

1. An `avg` row showing each player's all-time average solve time for that crossword game.
2. A `pb` row showing each player's all-time personal-best (lowest) solve time for that crossword game.

For Mini and Midi, all of a player's submitted days SHALL contribute to `avg` and `pb`. For Main, only days with `checkUsed == false`, `lookups == 0`/`null`, AND `duo == false` SHALL contribute (the day is fully excluded from both `avg` and `pb` if it fails any of those conditions). For Main, the calculation SHALL also be filtered to the same `DayOfWeek` as the scoreboard's date — e.g., a Sunday Main scoreboard considers only the player's clean Sunday Main submissions.

Today's submission (the row being rendered) SHALL be included in the calculation. When a player has no qualifying history for the row, the cell SHALL render as `-` (left-padded to the column width).

Times SHALL be rendered using the existing time formatter (e.g., `M:SS` under one hour, `H:MM:SS` over one hour) and `avg` SHALL be rounded to the nearest whole second before formatting.

The two new rows SHALL appear in the order `avg` then `pb`, separated from each other by a divider line whose `+` characters align with the `|` dividers in the surrounding rows.

#### Scenario: Mini scoreboard shows avg and pb across all submitted days
- **WHEN** a player has previously submitted Mini crossword times of 0:30, 0:50, 1:00 and submits 1:00 today
- **THEN** the Mini scoreboard's `avg` cell for that player reads `0:50` and the `pb` cell reads `0:30`

#### Scenario: Midi scoreboard shows avg and pb across all submitted days
- **WHEN** a player has previously submitted Midi crossword times of 1:20, 1:24 and submits 1:22 today
- **THEN** the Midi scoreboard's `avg` cell for that player reads `1:22` and the `pb` cell reads `1:20`

#### Scenario: Main avg and pb are filtered to the current weekday
- **WHEN** today is a Sunday and a player has Main submissions on a previous Saturday (10:00) and a previous Sunday (15:00) and submits 14:00 today (Sunday)
- **THEN** the Main scoreboard's `avg` cell reads `14:30` and the `pb` cell reads `14:00` (the Saturday row is excluded)

#### Scenario: Main excludes days with check, lookups, or duo from avg and pb
- **WHEN** a player has three Sunday Main submissions: 10:00 with `checkUsed = true`, 12:00 with `lookups = 2`, 14:00 with `duo = true`, and submits 16:00 today (Sunday) clean
- **THEN** the Main scoreboard's `avg` cell reads `16:00` and the `pb` cell reads `16:00` (only today qualifies)

#### Scenario: Player with no qualifying history renders as `-`
- **WHEN** a player has never submitted a clean Sunday Main and today's Sunday submission has `lookups = 1`
- **THEN** the Main scoreboard's `avg` and `pb` cells for that player both read `-`

#### Scenario: avg row rounds to the nearest whole second
- **WHEN** a player has Mini times of 0:30 and 0:31
- **THEN** the Mini scoreboard's `avg` cell for that player reads `0:31` (rounded from 30.5)

#### Scenario: avg over an hour formats with hours
- **WHEN** a player has Main Sunday times averaging 4530 seconds
- **THEN** the Main scoreboard's `avg` cell reads `1:15:30`

## MODIFIED Requirements

### Requirement: Main crossword daily comparison scoreboard
The system SHALL render a daily comparison scoreboard for the Main crossword in the results channel. The scoreboard's header SHALL display the day-of-week and date in the format `<DayOfWeek> - M/D/YYYY` (e.g., `Sunday - 5/10/2026`) — NOT the literal text `Main`. The scoreboard SHALL display each player's solve time side-by-side, with an outcome row showing the winner, a draw, or a "Nuke!" (☢️). When and only when at least one player has a flag set (duo, lookups, or check), the scoreboard SHALL display a flags row beneath the solve times showing the applicable flag indicators for each player. When neither player has any flag set, the flags row SHALL be omitted entirely (no blank row, no divider).

The check flag SHALL be rendered as `✅` (white heavy check mark) — NOT `✓`.

The win/draw/Nuke outcome SHALL be determined by the `checkUsed` and `lookups` flags on each player's `MainCrosswordResult`, NOT by `totalSeconds` alone. A player is considered to have "used assistance" when `checkUsed` is true OR `lookups` is greater than zero. The `duo` flag SHALL NOT influence the outcome.

The decision rules SHALL be:
1. If both players used assistance, the outcome is a generic tie ("🤝 Tie!"), regardless of time.
2. If exactly one player used assistance, the other player wins, regardless of time, and the win SHALL be displayed without a time differential (i.e., "🏆 [Winner] wins!").
3. If neither player used assistance and their times differ, the lower time wins with the time differential displayed in MM:SS format without a leading minus sign (e.g., "🏆 [Winner] wins! (0:12)").
4. If neither player used assistance and their times are identical, the outcome SHALL be "☢️ Nuke!".

#### Scenario: Main header shows day of week and date
- **WHEN** the Main scoreboard is rendered for a `crosswordDate` of 2026-05-10 (a Sunday)
- **THEN** the header reads `Sunday - 5/10/2026`

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

#### Scenario: Both players submitted Main crossword with flags row (check rendered as ✅)
- **WHEN** both players have a `mainCrosswordResult` and at least one player has `checkUsed = true`
- **THEN** the results channel displays the scoreboard with a flags row beneath the times, showing each player's flag indicators side-by-side (👫 for duo, 🔍×N for lookups, **✅** for check)

#### Scenario: Main flags row is omitted when no flags are set
- **WHEN** both players have a `mainCrosswordResult` and neither has any of `duo`, `checkUsed`, or `lookups > 0`
- **THEN** the rendered scoreboard contains no flags row and no divider for a flags row (it goes directly from the time row to the outcome row)

#### Scenario: One player submitted Main crossword
- **WHEN** only one player has a `mainCrosswordResult` on today's scoreboard
- **THEN** the results channel displays that player's time (and flags if set) with a waiting indicator for the other player

#### Scenario: Neither player submitted Main crossword
- **WHEN** neither player has a `mainCrosswordResult` on today's scoreboard
- **THEN** no Main crossword scoreboard is rendered

### Requirement: Crossword scoreboard header uses date
Mini and Midi crossword scoreboard headers SHALL display the crossword date in the format `<Type> Crossword - M/D/YYYY` (e.g., `Mini Crossword - 3/31/2026`, `Midi Crossword - 3/31/2026`). The Main crossword scoreboard header SHALL instead use the day-of-week and date format defined in the *Main crossword daily comparison scoreboard* requirement (e.g., `Sunday - 3/31/2026`).

#### Scenario: Mini header displays date
- **WHEN** a Mini crossword scoreboard is rendered for a result with `crosswordDate` of 2026-03-31
- **THEN** the header reads `Mini Crossword - 3/31/2026`

#### Scenario: Midi header displays date
- **WHEN** a Midi crossword scoreboard is rendered for a result with `crosswordDate` of 2026-03-31
- **THEN** the header reads `Midi Crossword - 3/31/2026`

#### Scenario: Main header uses day-of-week
- **WHEN** a Main crossword scoreboard is rendered for a result with `crosswordDate` of 2026-03-31 (a Tuesday)
- **THEN** the header reads `Tuesday - 3/31/2026`

## ADDED Requirements

### Requirement: Crossword scoreboard alignment
All three crossword scoreboards SHALL share consistent alignment rules for their non-emoji comparison rows. Wordle, Connections, and Strands scoreboards SHALL keep their existing emoji-grid layout unchanged.

1. **Name row**: The two-player name row SHALL render a centre `|` divider, with the left player name ending exactly 1 character before the divider and the right player name starting exactly 1 character after it.
2. **Score row**: The two-player score row SHALL render the same centre `|` divider as the name row, with the left score ending exactly 1 character before the divider and the right score starting exactly 1 character after it.
3. **Divider plus alignment**: The divider line between the name row and score row SHALL contain a `+` at the same column as the centre `|`. The `avg`/`pb` divider lines SHALL also keep their `+` characters aligned with their surrounding `|` dividers.
4. **Flag row padding**: Main crossword flag rows SHALL use the same centre divider as the time row when flags are present. Emoji glyphs used in that row SHALL be counted as 2 character widths when computing left-side padding; the `🔍×N` lookup indicator SHALL be counted as 2 (emoji) + the literal `×N` characters. ASCII characters SHALL be counted as 1 each.

#### Scenario: Lookup indicator with emoji is padded as 2-char emoji plus literal suffix
- **WHEN** a Main flags row renders `🔍×2` for a player
- **THEN** the column padding accounts for the indicator as 4 character widths (2 for the emoji + 2 for `×2`)

#### Scenario: Name and score cells sit one char from the centre divider
- **WHEN** any two-player crossword scoreboard name or score row is rendered
- **THEN** the rightmost character of the left cell is followed by exactly 1 space, then `|`, then exactly 1 space, then the leftmost character of the right cell

#### Scenario: Emoji scoreboards keep existing spacing
- **WHEN** a Wordle, Connections, or Strands scoreboard is rendered
- **THEN** its existing name/streak/emoji-grid spacing is unchanged by the crossword alignment rules

#### Scenario: avg/pb dividers have aligned plus characters
- **WHEN** a crossword scoreboard renders the divider line between the `avg` and `pb` rows
- **THEN** the divider line contains a `+` at the same column index as the centre `|` of the surrounding data rows
