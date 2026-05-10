## MODIFIED Requirements

### Requirement: Report content and ranking

For each requested game, the rendered report SHALL include:

- A leaderboard sorted by win count descending, with ties broken by best solve time ascending.
- For each player on the leaderboard: their rank (medal emoji for 1st/2nd/3rd, plain numbers thereafter), their display name, their win count, their average solve time across played days only, and their best solve time and the date of that best.
- For Main only, the average and best SHALL be computed from *clean* results only — that is, results where `duo` is not true AND `checkUsed` is not true AND `lookups` is null or zero. Assisted Main results SHALL NOT contribute to the average's numerator or denominator nor to the best. For Mini and Midi the average and best continue to use every saved result, since these games have no flag fields.
- For Main, when a player has one or more assisted results in the window that were excluded from the average and best computations, an immediately-following footnote line of the form `(N assisted excluded)` (where `N` is the count) SHALL appear beneath that player's row. The footnote SHALL be omitted when `N == 0`.

Win attribution SHALL remain unchanged from `crossword-win-streaks`: clean wins, forfeits, duo "no win", tie, and nuke semantics are not affected by this requirement.

Times SHALL be formatted as `m:ss` for sub-hour values and `h:mm:ss` for values of one hour or more. Average times SHALL be rounded to the nearest second.

#### Scenario: Leaderboard ordering by wins
- **GIVEN** Player A has 5 wins and Player B has 3 wins for a game in the window
- **WHEN** the report is rendered
- **THEN** Player A is ranked 1st and Player B is ranked 2nd

#### Scenario: Tie-break by best time
- **GIVEN** Player A and Player B both have 4 wins, with best times 0:32 and 0:28 respectively
- **WHEN** the report is rendered
- **THEN** Player B is ranked above Player A

#### Scenario: Average uses played days only
- **GIVEN** in a 7-day window Player A submitted on 4 days with times totalling 8:00 and Player B submitted on 7 days with times totalling 21:00
- **WHEN** the report is rendered
- **THEN** Player A's average is shown as `2:00` (8:00 / 4) and Player B's average is shown as `3:00` (21:00 / 7)

#### Scenario: Time format scales with magnitude
- **WHEN** a Main average is `1:23:45`
- **THEN** the report renders it as `1:23:45`, not `83:45` or `5025`

#### Scenario: Main average and best exclude assisted results
- **GIVEN** in a window Player A has 5 Main results: 3 clean (totals 30:00) and 2 assisted (one with `lookups = 2`, one with `duo = true`, totals 10:00)
- **WHEN** the report is rendered
- **THEN** Player A's average is `10:00` (30:00 / 3) and the best is the lowest *clean* time, not the lowest overall time

#### Scenario: Excluded-assisted footnote appears under the player row
- **GIVEN** Player A has 3 assisted Main results in the window that were excluded from average and best
- **WHEN** the Main report is rendered
- **THEN** Player A's row is followed by a `(3 assisted excluded)` footnote line

#### Scenario: No footnote when nothing was excluded
- **GIVEN** Player A has zero assisted Main results in the window
- **WHEN** the Main report is rendered
- **THEN** no footnote line follows Player A's row

#### Scenario: Mini and Midi never produce a footnote
- **WHEN** a Mini or Midi report is rendered
- **THEN** no `(N assisted excluded)` footnote line ever appears, since Mini and Midi have no flag fields
