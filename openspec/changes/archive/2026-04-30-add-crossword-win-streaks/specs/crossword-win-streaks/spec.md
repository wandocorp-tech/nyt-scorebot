## ADDED Requirements

### Requirement: Persist crossword win streaks per player per game

The system SHALL persist a `WinStreak` record per (user, crossword game type) combination. The supported game types are `MINI_CROSSWORD`, `MIDI_CROSSWORD`, and `MAIN_CROSSWORD`. Each record SHALL store the current streak value, a frozen snapshot of the previous day's streak (`base_streak`), the date that snapshot represents (`base_date`), the date the record was last computed (`computed_date`), and a `last_updated` timestamp.

#### Scenario: First-time computation creates a record
- **WHEN** the win streak service is asked to update a (user, game) pair that has no existing `WinStreak` row
- **THEN** a new row is created with `current_streak`, `base_streak`, and `base_date` initialized as if the user had a streak of 0 yesterday, and the outcome is then applied

#### Scenario: Records are unique per user and game
- **WHEN** the system attempts to persist two `WinStreak` rows for the same user and the same game type
- **THEN** the database SHALL reject the second row via a unique constraint on (`user_id`, `game_type`)

### Requirement: Recompute crossword win streaks when both players finish

The system SHALL recompute all three crossword win streaks (Mini, Midi, Main) whenever the results channel refreshes its scoreboards because both players have finished their daily submissions.

#### Scenario: Both players finish triggers a full recompute
- **WHEN** the second player submits their final game of the day and the results channel `refresh()` runs
- **THEN** the win streak service computes outcomes for Mini, Midi, and Main using the existing scoreboard comparison logic and updates each `WinStreak` row accordingly

#### Scenario: Recompute happens before the summary message is posted
- **WHEN** the results channel posts its scoreboards and summary
- **THEN** the win streak recompute SHALL complete before the summary message is built so that the displayed values reflect today's results

### Requirement: Recompute Main win streak on Main flag changes

The system SHALL recompute the Main crossword win streak (and only the Main streak) whenever the Main crossword scoreboard is refreshed via a flag change (`/duo`, `/check`, or `/lookups`). The summary message SHALL be edited in place to reflect the recomputed value.

#### Scenario: /duo set after Main was already counted as a win
- **GIVEN** the Main crossword has already been recomputed today and the winner's `current_streak` was incremented to N
- **WHEN** the winner subsequently sets `/duo`
- **THEN** the win streak service recomputes the Main streak using the snapshotted `base_streak`, restoring `current_streak` to its pre-Main value, and the summary message is edited to show the corrected values

#### Scenario: /duo cleared, restoring the win
- **GIVEN** the Main crossword was previously recomputed with `duo=true` and the winner's `current_streak` was held at the base value
- **WHEN** the winner clears `/duo`
- **THEN** the win streak service recomputes and the winner's `current_streak` is set to `base_streak + 1` (or `1` if there was a gap), and the summary message is edited to reflect this

### Requirement: Snapshot the previous day's value once per day

On the first win streak update for a given (user, game) pair on a given date, the system SHALL snapshot the existing `current_streak` into `base_streak` and copy the existing `computed_date` into `base_date` before applying the new outcome. Subsequent updates on the same date SHALL NOT overwrite the snapshot.

#### Scenario: First update of the day snapshots
- **GIVEN** a `WinStreak` row with `current_streak = 4`, `computed_date = yesterday`
- **WHEN** the win streak service runs for the first time today
- **THEN** before applying the outcome, the row is updated to `base_streak = 4`, `base_date = yesterday`, `computed_date = today`

#### Scenario: Same-day re-run preserves the snapshot
- **GIVEN** a `WinStreak` row already updated today with `base_streak = 4`, `base_date = yesterday`, `computed_date = today`, `current_streak = 5`
- **WHEN** the win streak service runs again today (e.g., due to a flag change)
- **THEN** `base_streak` and `base_date` remain `4` and `yesterday`, only `current_streak` and `last_updated` are recomputed

### Requirement: Apply outcome-specific transitions

The system SHALL translate each `ComparisonOutcome` produced by the crossword scoreboard into win streak changes per the following rules. All changes are computed relative to the snapshotted `base_streak`.

| Outcome | Winner action | Loser action |
|---|---|---|
| Clean Win (`Win`, winner did not use duo) | `current = base + 1` if `today - base_date == 1`, else `current = 1` | `current = 0` |
| Duo Win (`Win`, winner has `duo=true`) | `current = base` (no change) | `current = base` (no change) |
| Tie (`Tie` — both used assistance, Main only) | `current = 0` | `current = 0` |
| Nuke (`Nuke` — equal clean times) | `current = base` (no change) | `current = base` (no change) |
| Waiting (`WaitingFor` — at least one player missing the game, **before midnight**) | `current = base` (no change) | `current = base` (no change) |

#### Scenario: Clean win on consecutive days extends the streak
- **GIVEN** a `WinStreak` row with `base_streak = 3`, `base_date = yesterday`
- **WHEN** the outcome is a clean Win for this user
- **THEN** `current_streak` is set to `4`

#### Scenario: Clean win after a gap restarts at 1
- **GIVEN** a `WinStreak` row with `base_streak = 5`, `base_date = three days ago`
- **WHEN** the outcome is a clean Win for this user
- **THEN** `current_streak` is set to `1`

#### Scenario: Clean win resets the loser
- **GIVEN** a `WinStreak` row with `base_streak = 7` for the losing player
- **WHEN** the outcome is a clean Win for the other player
- **THEN** the losing player's `current_streak` is set to `0`

#### Scenario: Duo win does not change either streak
- **GIVEN** `WinStreak` rows with `base_streak = 5` (winner) and `base_streak = 2` (loser)
- **WHEN** the outcome is a Win where the winner has `duo=true`
- **THEN** the winner's `current_streak` is restored to `5` and the loser's `current_streak` is restored to `2`

#### Scenario: Tie resets both streaks
- **GIVEN** `WinStreak` rows with `base_streak = 4` and `base_streak = 6`
- **WHEN** the outcome is a `Tie` (both used assistance on Main)
- **THEN** both `current_streak` values are set to `0`

#### Scenario: Nuke does not change either streak
- **GIVEN** `WinStreak` rows with `base_streak = 3` for each player
- **WHEN** the outcome is `Nuke`
- **THEN** each player's `current_streak` is restored to `3`

#### Scenario: Waiting does not change either streak
- **GIVEN** `WinStreak` rows with `base_streak = 2` for each player
- **WHEN** the outcome is `WaitingFor` (e.g., one player has not submitted that crossword) **and the puzzle day has not yet ended**
- **THEN** each player's `current_streak` is restored to `2`

### Requirement: Apply forfeit rules at midnight rollover

The system SHALL run a scheduled job at 00:00 in the puzzle timezone (the same timezone used by `PuzzleCalendar`) that finalizes win streaks for the day that just ended. For each crossword game type (Mini, Midi, Main):

| Submission status (yesterday) | Submitter (or both, if both submitted) | Non-submitter |
|---|---|---|
| Both players submitted | No-op (the daytime computation already ran; the snapshot guard makes a re-run safe but the job SHOULD skip to avoid pointless writes) |
| Exactly one player submitted | Treat the submitter as a clean Win: `current = base + 1` if `today - base_date == 1` else `current = 1` | `current = 0` |
| Neither player submitted | `current = 0` | `current = 0` |

After the forfeit pass completes, the previous day's win streak summary message SHALL be edited in place to reflect the finalized values.

#### Scenario: Solo submitter wins by forfeit
- **GIVEN** Player A submitted yesterday's Mini crossword and Player B did not, and at the end of the day Player A's `WinStreak` row has `base_streak = 4`, `base_date = day-before-yesterday`
- **WHEN** the midnight job runs
- **THEN** Player A's `current_streak` is set to `5` and Player B's `current_streak` is set to `0`, and yesterday's summary message is edited to reflect these values

#### Scenario: Neither player submits
- **GIVEN** neither player submitted yesterday's Midi crossword and both players had `current_streak = 3`
- **WHEN** the midnight job runs
- **THEN** both players' `current_streak` values are set to `0`, and yesterday's summary message is edited to reflect this

#### Scenario: Forfeit applies snapshot semantics correctly
- **GIVEN** Player A submitted yesterday's Main crossword and Player B did not, and Player A's `base_streak` was last snapshotted on day-before-yesterday
- **WHEN** the midnight job runs and the win streak service is invoked for yesterday's date
- **THEN** the snapshot logic recognizes `computed_date = yesterday` (set during yesterday's `WaitingFor` runs) is the same as the recompute date, the snapshot is NOT overwritten, and the gap calculation uses `yesterday - base_date == 1` to award `base + 1`

#### Scenario: Both submitted is a no-op at midnight
- **GIVEN** both players submitted all crossword games yesterday and the win streak service ran during the day via `refresh()`
- **WHEN** the midnight job runs
- **THEN** no `WinStreak` rows are modified for those games and no message edit occurs

### Requirement: Resolve duo winners to underlying user

When the `Win` outcome's `winnerName` includes the " et al." suffix used to indicate a duo, the system SHALL resolve the streak update to the underlying `User` via the `Scoreboard.user` relation, NOT by parsing the display name.

#### Scenario: Duo winner is correctly identified
- **WHEN** the outcome is a Win whose `winnerName` is "Player A et al."
- **THEN** the streak update is applied to the `User` whose Main crossword scoreboard produced the winning row, regardless of how the display name is formatted

### Requirement: Display the win streak summary message

After both players have finished and the six daily scoreboards have been posted, the system SHALL post a single "win streak summary" message in the results channel. The message SHALL be formatted as a code-block table with one row per crossword game (Mini, Midi, Main) and one column per player, showing each player's current streak. Streaks of three or more SHALL be marked with a 🔥 emoji.

#### Scenario: Summary is posted after the scoreboards
- **WHEN** the results channel finishes posting all six daily scoreboard messages
- **THEN** a seventh message containing the win streak summary table is posted in the same channel

#### Scenario: Summary is edited in place on flag changes
- **GIVEN** the win streak summary has already been posted today
- **WHEN** the Main crossword is refreshed due to a flag change and the win streak is recomputed
- **THEN** the existing summary message is edited (not re-posted) to reflect the recomputed values

#### Scenario: Streak ≥ 3 displays the fire emoji
- **WHEN** a player's `current_streak` for any crossword is `3` or higher
- **THEN** the summary message renders the value with a trailing 🔥 emoji

#### Scenario: Streak of 0 displays plainly
- **WHEN** a player's `current_streak` is `0`
- **THEN** the summary message renders `0` with no emoji decoration

### Requirement: Do not modify existing completion streak behavior

The system SHALL NOT alter the existing `Streak` entity, `StreakService`, `/streak` slash command, or completion-streak rows shown on the Wordle/Connections/Strands scoreboards. Crossword win streaks are stored, computed, and displayed entirely separately.

#### Scenario: /streak command output is unchanged
- **WHEN** a user runs the `/streak` slash command after this change is deployed
- **THEN** the response contains exactly the same per-player completion streaks for Wordle, Connections, and Strands as before, with no crossword data added
