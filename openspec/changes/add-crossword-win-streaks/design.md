## Context

The bot already tracks **completion streaks** for the emoji-based games via the `Streak` entity, `StreakService`, and the `/streak` slash command. Crossword games are explicitly excluded from that system because completion alone is not the relevant measure for a head-to-head game.

The recently archived change `2026-04-27-refine-main-crossword-win-logic` formalized the crossword outcome model: each crossword scoreboard now produces a `ComparisonOutcome` (a sealed interface with `Win`, `Tie`, `Nuke`, and `WaitingFor` variants). This gives us a clean source of truth for "who won today?".

The natural trigger for win streak updates is `ResultsChannelService.refresh()`, which fires when both players have submitted all their results for the day. However, the Main crossword has a critical timing wrinkle: the `/duo` flag is often set by the player who finished last, *seconds after* the Main crossword was submitted. By that time, `refresh()` has already run and computed a "win" that — once `/duo` is applied — should not have counted toward the streak. The flag-toggle path already calls `refreshGame(Main)` to redraw the scoreboard; we will piggy-back on that to recompute the Main win streak as well.

## Goals / Non-Goals

**Goals:**
- Track crossword win streaks (Mini, Midi, Main) separately from existing completion streaks.
- Display a compact "win streak summary" message after the six scoreboards in the results channel.
- Tolerate late-arriving flag changes (`/duo`, `/check`, `/lookups`) without double-counting or losing the previous day's anchor.
- Avoid walking the full scoreboard history on every refresh.

**Non-Goals:**
- Tracking duos as their own streak metric. Duo wins simply don't count; if/when we want a "best duo streak" it can be a follow-up change.
- Changing how the existing `Streak`/`StreakService` system works.
- Extending the `/streak` slash command to surface crossword win streaks. The summary message in the results channel is the only UI surface in this change.
- Backfilling historical streaks from existing scoreboard rows.

## Decisions

### Decision 1: New `WinStreak` entity, separate from `Streak`

Win streaks and completion streaks are conceptually different: completion streaks are per-player ("did you finish today?"), win streaks are head-to-head ("did you beat the other player today?"). They can also diverge in value — a player can complete every Wordle but lose every Mini.

**Alternatives considered:**
- *Add a discriminator column to `Streak`* — would tangle two different update semantics into one service. Rejected.
- *Reuse `StreakService` with new `GameType` cases* — same problem; the `StreakService.update(...)` API is per-player completion, not pairwise.

**Chosen:** New `WinStreak` entity, new `WinStreakRepository`, new `WinStreakService`. Existing streak code is untouched.

### Decision 2: Snapshot-based recompute (the "base streak" pattern)

Win streaks must be recomputable mid-day because flag changes can arrive after the initial computation. A naive approach that just increments/resets `current_streak` every time would either double-count (re-running a "win" recompute) or lose history (overwriting yesterday's value with today's "no change" before we know today's outcome).

**Chosen:** Each `WinStreak` row stores:
- `current_streak` — the displayed value
- `base_streak` — yesterday's frozen value, snapshotted on the **first** update of today
- `base_date` — the date `base_streak` represents (i.e., the previous `computed_date`)
- `computed_date` — the date of the last update; gates the snapshot logic
- `last_updated` — bookkeeping

**Recompute algorithm** (per player, per game type):
1. If `computed_date != today`: snapshot `base_streak = current_streak`, `base_date = computed_date`, set `computed_date = today`.
2. If `computed_date == today`: skip snapshot (already done earlier today).
3. Apply outcome to compute new `current_streak`:
   - **Win**: if `today - base_date == 1` then `base_streak + 1`; else `1` (gap day means streak restarted).
   - **Loss**: `0`.
   - **No change** (Nuke/Duo win/missing crossword): restore `current_streak = base_streak`.

This is idempotent: re-running with the same outcome yields the same `current_streak`. It also correctly handles the duo case — first run records a Win (current = base+1), then `/duo` recompute records a No-change (current restored to base).

**Alternatives considered:**
- *Walk scoreboard history on every refresh* — clean but wasteful; rejected as "ugly" by the user.
- *Lock the streak after the first compute of the day* — would either freeze the wrong outcome or require manual override commands. Rejected.

### Decision 3: Trigger points

- `ResultsChannelService.refresh()` (both players done): recompute all three crosswords, post the summary message after the six scoreboards.
- `ResultsChannelService.refreshGame(Main)` (called by `FlagReplyHelper.refreshMainCrossword`): recompute *just* Main, edit the existing summary message in place.
- `refreshGame(Mini|Midi)` does NOT exist today and is not added — Mini/Midi flags don't exist; they're flag-free games.

### Decision 4: Summary message format

A status-board-style code block, one row per crossword, one column per player. Example:

```
🏆 Crossword Win Streaks
┌──────┬──────────┬──────────┐
│ Game │ Player A │ Player B │
├──────┼──────────┼──────────┤
│ Mini │     5 🔥 │      0   │
│ Midi │     2    │      1   │
│ Main │     0    │      3 🔥 │
└──────┴──────────┴──────────┘
```

Reuses the same Unicode box-drawing approach as `StatusMessageBuilder` for visual consistency. A 🔥 emoji marks streaks ≥ 3 (matching existing convention in the completion streak rows).

### Decision 5: Outcome → action mapping

| ComparisonOutcome | Winner action | Loser action |
|---|---|---|
| `Win` (winner duo=false) | +1 / restart from base+1 if gap | reset to 0 |
| `Win` (winner duo=true) | restore base | restore base |
| `Tie` (Main, both assisted) | reset to 0 | reset to 0 |
| `Nuke` (equal clean times) | restore base | restore base |
| `WaitingFor` (one or both missing, **before midnight**) | restore base | restore base |

The `Win` record's `winnerName` may include " et al." when duo; the streak service must resolve back to the underlying `User` via the `Scoreboard`/`User` relation, not parse the display name.

### Decision 6: Midnight rollover for unsubmitted games

Crosswords are a daily commitment — failing to submit before the day rolls over should break the streak. We can't rely on the existing `refresh()` trigger because that only fires when *both* players have finished, and a forfeit is precisely the case where one (or both) hasn't.

**Chosen:** A Spring `@Scheduled` job (`WinStreakMidnightJob`) runs at 00:00 in the puzzle timezone (GMT/BST, same as `PuzzleCalendar`). For each crossword game type and each player pair, it inspects yesterday's scoreboards and applies a forfeit outcome:

| Submission status | Action |
|---|---|
| Both players submitted (Win/Tie/Nuke already applied) | No-op — the snapshot guard makes re-running a same-day computation idempotent and safe, but the job skips when both scoreboards have the game to avoid pointless writes |
| Only one player submitted | Submitter: same as a clean Win (`base + 1` if consecutive, else `1`); non-submitter: reset to 0 |
| Neither player submitted | Both: reset to 0 |

The job runs *just after midnight* and operates on **yesterday's** date so the snapshot anchor remains correct (the snapshot logic uses `today = yesterday` for the recompute it performs, freezing the day-before-yesterday's value as the new base). After the job runs, it edits yesterday's summary message in place to show the finalized values. The next day starts fresh and the next `refresh()` will snapshot the freshly finalized values cleanly.

**Alternatives considered:**
- *Resolve forfeits lazily on the next day's first refresh* — works but means streaks display stale "no change" values overnight, which is misleading to anyone viewing the channel before submitting. Rejected.
- *Resolve forfeits inside `refresh()` whenever it runs* — can't, because `refresh()` doesn't run when neither player has finished.

## Risks / Trade-offs

- **[Risk] Timing race between scoreboard post and flag command** → `current_streak` displayed in the first summary is provisional. Mitigation: the `/duo` (etc.) handler edits the summary message in place via `refreshGame(Main)` within seconds, so the discrepancy is short-lived. The snapshot pattern guarantees the eventual value is correct regardless of how many recomputes fire.
- **[Risk] Snapshot taken on a non-final outcome (e.g., refresh fires partway through the day)** → The snapshot only captures the *previous day's* value, so an in-day re-run never overwrites yesterday's anchor. The only way to corrupt the base would be to call recompute on a date with no earlier compute that day, which is exactly when the snapshot is supposed to fire. Safe by construction.
- **[Risk] Edge case: missed days** → If both players skip a day (no `refresh()` runs), the next day's compute will see `today - base_date > 1` and treat the streak as broken (resetting to 1 on a Win, 0 on a Loss). This matches the "consecutive days" intent of a streak.
- **[Trade-off] Summary message is posted/edited via Discord4J `MessageChannel.createMessage()` and `Message.edit()`** → another message ID to track per channel. We'll persist it the same way the existing scoreboard message IDs are persisted (`ResultsChannelService` already maintains a per-game message ID map; add a `WIN_STREAK_SUMMARY` slot).
- **[Trade-off] No backfill** → Existing scoreboards from before this change won't seed initial streaks. Players start at 0; first day's results will produce 1/0 values. Acceptable given the small history.

## Migration Plan

1. Hibernate `ddl-auto=update` creates the `win_streak` table on first boot. No manual SQL needed.
2. On first `refresh()` after deploy, all streaks start at 0 and update from that day's results.
3. Rollback: drop the `win_streak` table (or leave it; it's unread by older code). Revert the deployment. No other artifacts touched.

## Open Questions

- Should the summary message be re-posted as a fresh message each day, or edited in place across days? **Tentative answer:** posted fresh each day, since the scoreboards are also posted fresh each day. The `WIN_STREAK_SUMMARY` message ID slot is per-day, same as the scoreboard slots.
