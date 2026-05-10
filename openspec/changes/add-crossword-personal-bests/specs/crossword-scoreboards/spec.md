## ADDED Requirements

### Requirement: Daily scoreboards include inline avg-delta and PB rows

Every per-game crossword scoreboard rendered by `ScoreboardRenderer` (Mini, Midi, Main; both two-player and single-player layouts) SHALL include, between the existing score / flags rows and the existing outcome / streak row, the following extra rows when the player has at least one prior clean result for that game (and for Main, that day-of-week):

1. A single `Δ avg` header row, centered between the two columns.
2. A signed-delta row containing the difference between today's solve time and the player's average of all prior clean results, formatted as `±M:SS` (a leading `+` or `-` sign and `M:SS` magnitude). Negative means today is faster than average.
3. A `PB:M:SS` row showing the player's current personal best for that `(game, dayOfWeek)`.

Rows 1–3 SHALL use only ASCII characters (no arrow glyphs, no triangle glyphs, no emoji) for direction indication. The full rendered scoreboard SHALL not exceed `BotText.MAX_LINE_WIDTH` (33 columns) on any line. For Main, the average and PB SHALL be drawn from results matching the puzzle's day-of-week only. For Mini and Midi, the average and PB SHALL be drawn from all the player's clean results for that game.

#### Scenario: Two-player Main scoreboard with full history shows all three new rows
- **GIVEN** both players have at least one prior clean Main result on the same day-of-week and a current `personal_best` row for that day-of-week
- **WHEN** the Main scoreboard is rendered for today
- **THEN** the rendered text contains a `Δ avg` header row, a signed delta row of the form `<sign>M:SS     <sign>M:SS`, and a `PB:M:SS     PB:M:SS` row, each within 33 columns

#### Scenario: Today's time slower than average renders a positive delta
- **WHEN** today's clean time is `12:34` and the player's prior clean average is `10:19`
- **THEN** the delta cell for that player renders as `+2:15`

#### Scenario: Today's time faster than average renders a negative delta
- **WHEN** today's clean time is `9:00` and the player's prior clean average is `11:15`
- **THEN** the delta cell for that player renders as `-2:15`

#### Scenario: Main avg/PB use day-of-week values
- **GIVEN** today is Saturday, the player's overall Main average is `15:00`, and their Saturday-only Main average is `22:00`, with a Saturday `personal_best` row of `19:30`
- **WHEN** the Main scoreboard is rendered
- **THEN** the delta is computed against `22:00` (not `15:00`) and the PB row reads `PB:19:30`

#### Scenario: Mini avg/PB ignore day-of-week
- **WHEN** a Mini scoreboard is rendered for any day
- **THEN** the avg and PB are computed from the player's all-time clean Mini results regardless of day-of-week

### Requirement: Empty history omits the new rows for that player

When a player has no prior clean result for the relevant `(game, dayOfWeek)` pair, the avg-delta cell and the `PB:` cell for that player SHALL be rendered as blank padding so that the other player's column still aligns. When *neither* player has a prior clean result, the `Δ avg` header row, the delta row, and the `PB:` row SHALL be omitted entirely from the scoreboard.

#### Scenario: One player has history, the other does not
- **GIVEN** player A has prior clean Saturday Main results and a Saturday `personal_best` row, and player B has none
- **WHEN** the Main scoreboard is rendered on a Saturday
- **THEN** the Δ avg / delta / PB rows are present, player A's column shows the values, and player B's column shows blank padding of the same column width

#### Scenario: Neither player has history for that DoW
- **GIVEN** neither player has a prior clean Saturday Main result
- **WHEN** the Main scoreboard is rendered on a Saturday
- **THEN** the rendered scoreboard contains no `Δ avg` header, no delta row, and no PB row, and the existing outcome / streak row immediately follows the score / flags row

#### Scenario: Mini scoreboard for a brand-new player
- **GIVEN** player A has just submitted their first-ever Mini result and player B has never submitted a Mini
- **WHEN** the Mini scoreboard is rendered
- **THEN** the new rows are omitted entirely (player A has no *prior* result to average against, and the new save itself is not included in the delta sample)

### Requirement: Avg sample uses prior clean results strictly before today

The "average" used for the delta row SHALL be the arithmetic mean of all clean results for that player and `(game, dayOfWeek)` whose `Scoreboard.date` is strictly earlier than today's date. Today's just-saved result SHALL NOT be included in the divisor; otherwise, the very first save of a new PB would underplay its own delta. Assisted Main results SHALL NOT contribute to either the numerator or the denominator.

#### Scenario: Today's save is excluded from its own delta calculation
- **GIVEN** the player has exactly one prior clean Main result on Saturday with `totalSeconds = 600`
- **WHEN** the player saves a clean Saturday Main result of `totalSeconds = 900` and the scoreboard is rendered
- **THEN** the player's average is `10:00` (computed from the one prior result alone) and the delta cell renders as `+5:00`

#### Scenario: Assisted history excluded from average
- **GIVEN** the player has two prior Main Saturday results: one clean (`totalSeconds = 800`) and one with `lookups = 1` (`totalSeconds = 400`)
- **WHEN** the scoreboard is rendered for a clean Saturday Main save
- **THEN** the average uses only the `totalSeconds = 800` result (`avg = 13:20`) and the assisted result is excluded from the divisor
