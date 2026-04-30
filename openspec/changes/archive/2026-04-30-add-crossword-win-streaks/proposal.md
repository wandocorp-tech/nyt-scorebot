## Why

The bot currently tracks **completion streaks** for the emoji-based games (Wordle, Connections, Strands), rewarding consistent daily play. The crossword games (Mini, Midi, Main) have no equivalent recognition for sustained dominance over the other player. A **win streak** — counting consecutive head-to-head crossword victories — provides an ongoing metric that fits the competitive nature of the crossword scoreboards and adds a new layer of bragging rights.

Win streaks are conceptually distinct from completion streaks: they require comparison between two players (not just per-player success), and they only meaningfully apply to the crossword games where the existing scoreboards already produce a deterministic winner.

## What Changes

- **Add a new `WinStreak` entity** stored in a separate `win_streak` table. It mirrors the structure of the existing `Streak` entity but is scoped to crossword game types only and updated based on head-to-head outcomes rather than per-player success.
- **Add a `WinStreakService`** that updates win streaks based on the `ComparisonOutcome` produced by the existing crossword scoreboard classes. Win streak changes use a `base_streak` snapshot pattern so that mid-day flag changes (e.g., `/duo` set seconds after submission) recompute correctly without double-counting.
- **Trigger win streak updates from `ResultsChannelService.refresh()`** — the existing entry point that fires when both players have finished. Updates run before the scoreboard messages are posted.
- **Re-trigger win streak updates when Main crossword flags change** — `/duo`, `/check`, and `/lookups` already call `refreshGame(Main)`. This same path will recompute the Main win streak (using the snapshotted base) and refresh the streak summary message.
- **Post a new "win streak summary" message** in the results channel after the six scoreboards. Format: a compact code-block table with one row per crossword game and one column per player, showing each player's current win streak.
- **Define streak transition rules per outcome:**
  - Clean Win → winner +1, loser → 0
  - Nuke (equal clean times) → no change for either player
  - Duo win (winner has `duo=true`) → no change for either player (revisit if duos get separate tracking)
  - Tie (both used assistance, Main only) → both → 0
  - Either player missing the crossword *during the day* → no change for either player (waiting; they may still submit)
- **Add a midnight rollover** that finalizes any unsubmitted crosswords at 00:00 GMT/BST (the same timezone used by `PuzzleCalendar`):
  - One player submitted, other did not → submitter +1, non-submitter → 0 (forfeit)
  - Neither submitted → both → 0
  - The summary message for the now-prior day is edited in place to reflect the finalized values, then the next day starts with a clean slate.
- **Existing `Streak` entity, `StreakService`, `/streak` slash command, and emoji-game scoreboard streak rows are unchanged.** Completion streak tracking continues to work exactly as today.

## Capabilities

### New Capabilities
- `crossword-win-streaks`: Persistence and head-to-head update logic for crossword win streaks (Mini, Midi, Main), display of the win streak summary message in the results channel, and snapshot-based recomputation that tolerates late-arriving flag changes.

### Modified Capabilities
<!-- None - crossword-scoreboards capability is unchanged; win streak update is additive and lives outside the scoreboard rendering pipeline. -->

## Impact

- **New entity & repository**: `WinStreak` (in `nyt-scorebot-database`) with `WinStreakRepository`. Schema is auto-created by Hibernate `ddl-auto=update`. No migration of existing data.
- **New service**: `WinStreakService` (in `nyt-scorebot-service`) implementing the snapshot-based recompute logic.
- **New helper**: `WinStreakSummaryBuilder` (in `nyt-scorebot-service`) building the table-formatted summary message.
- **Modified service**: `ResultsChannelService` (in `nyt-scorebot-discord`) gains a streak update step in `refresh()` and `refreshGame()`, and posts/edits the new summary message.
- **New scheduled job**: `WinStreakMidnightJob` (`@Scheduled` cron at 00:00 in the puzzle timezone) finalizes any unsubmitted crosswords from the prior day, applies forfeit rules, and edits the prior day's summary message.
- **Modified helper**: `FlagReplyHelper.refreshMainCrossword()` indirectly triggers win streak recompute via `refreshGame(Main)`. No change to the helper itself.
- **`BotText`**: New constants for the summary message (header, row labels, formatting tokens).
- **No changes** to the existing `Streak` entity, `StreakService`, `StreakCommandHandler`, scoreboard rendering, or completion streak behavior.
- **Tests**: New unit tests for `WinStreakService` (covering all outcome transitions, base snapshot logic, and same-day re-runs), `WinStreakSummaryBuilder`, and updated `ResultsChannelServiceTest` to verify the summary message is posted after the scoreboards.
