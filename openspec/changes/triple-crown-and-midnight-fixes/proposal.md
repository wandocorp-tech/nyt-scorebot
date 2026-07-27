## Why

Three unrelated gaps in the daily results flow, bundled for a single review and release:

1. **No recognition for a clean sweep.** Winning all three crosswords in one day is the strongest single-day performance in the game and currently passes without comment.
2. **End-of-day results silently fail to publish.** `StatusBoardMidnightJob` is supposed to force-post yesterday's boards when neither player triggered the "both finished" path, but in production nothing is posted. The code path is unit-tested and passes, so the failure is environmental or in an untested seam — it needs diagnosis before it can be fixed.
3. **A corrupted historical result is poisoning the stats.** William's 2026-05-17 Main Crossword was submitted before `CrosswordParser` handled `HH:MM:SS`, so `1:10:25` was stored as `1:10` (70s) and became his Sunday personal best. Two prior migrations (V9, V10) attempted the fix and both silently matched zero rows because they gate on `app_user.name = 'William'`, while the name written by `ScoreboardService` comes from `discord.channels[0].name`, which is `Will`. The same V8 migration seeded Main Crossword PBs for `'William'` and therefore also never applied.

## What Changes

### Triple Crown celebration

- Add a triple crown detection rule: a player wins all three crosswords (Mini, Midi, Main) on the same day.
- A win by forfeit or opponent disqualification (`ComparisonOutcome.Win` with a null differential) **counts** toward the crown.
- A `/duo` flag on the **winner's** Main Crossword result **blocks** the crown. A `/duo` flag on the loser's result does not.
- Post the celebration as its own message in the results channel, last — after all game boards and after the win-streak summary.
- Track the message in a dedicated in-memory slot so re-renders are idempotent.
- If a later flag change (`/duo`, `/check`, `/lookups`) means the crown is no longer earned, **delete** the message and clear the slot.
- The crown SHALL also be evaluated during the midnight force-post, after win-streak forfeits have been applied for the day.
- Wordle, Connections, and Strands are excluded from the crown.

### Midnight force-post

- **Diagnose first.** Add structured logging at each early-return in `ResultsChannelService.prepareContext` and `StatusBoardMidnightJob.forcePostYesterdayIfNeeded` so a silent no-op is distinguishable in the service log from a Discord write failure.
- Fix the root cause once identified. The leading hypothesis is a name-coupling defect: `prepareContext` builds its lookup map keyed on `Scoreboard.getUser().getName()` (a database value) and reads it with `DiscordChannelProperties` channel names (a config value). Any divergence yields `sb1 == null` and `sb2 == null`, which makes `renderAll` return an empty map, which writes nothing at all — with no log line.
- Decouple player identification from the mutable display name: resolve scoreboards by `discord_user_id` (immutable) rather than by `name`.
- Guarantee deterministic ordering between the two 00:00 jobs, so win-streak forfeits are applied before the results boards and triple crown are rendered.

### Historical data fix

- Add migration **V11** to correct the 2026-05-17 Main Crossword row, rebuild the affected `crossword_history_stats` bucket, and re-apply the V8 PB seeds that never landed.
- Gate V11 on `app_user.discord_user_id` rather than `app_user.name`, so a future display-name change cannot silently break it again.
- Preserve the manually-seeded Sunday PB: the rebuilt bucket takes `LEAST(rebuilt_min, seeded_pb)` so a genuine pre-bot PB is never regressed by a recompute.
- V8, V9, and V10 are **left untouched** — they are already applied in production and editing them would break Flyway checksum validation and prevent the application from starting.
- Rename the `"William"` display-name literals in test fixtures to `"Will"` for consistency with the deployed configuration.

## Capabilities

### New Capabilities
- `triple-crown-celebration`: detection rules, posting position, revocation, and interaction with duo/forfeit outcomes for the all-three-crosswords sweep.
- `data-migration-safety`: rules that make a data-correcting migration fail loudly instead of silently matching zero rows — immutable gate keys, and a test that seeds the corrupted state.

### Modified Capabilities
- `results-message-lifecycle`: adds a delete-and-clear lifecycle for a conditional slot (the crown message), which the current spec does not cover — it assumes every slot is edited in place forever. Also adds the requirement that scoreboards are resolved by immutable Discord user ID rather than display name, and that early returns are logged.
- `crossword-win-streaks`: adds the requirement that midnight forfeit application is ordered before the results render, so the crown and boards reflect finalized streaks.

## Impact

**Code**

| Area | Change |
|---|---|
| `nyt-scorebot-service` | New triple crown detection service; `CrosswordWinStreakService` exposes per-game outcomes for reuse |
| `nyt-scorebot-discord` | `ResultsChannelService` — crown slot, delete path, ID-based scoreboard lookup, early-return logging; `MessageSlotWriter` — a delete operation |
| `nyt-scorebot-discord` | `StatusBoardMidnightJob` / `WinStreakMidnightJob` — deterministic ordering |
| `nyt-scorebot-domain` | `BotText` — crown message strings |
| `nyt-scorebot-database` | New `V11` migration |

**Data**

- One `game_result` row corrected (2026-05-17, Main Crossword, Will).
- One `crossword_history_stats` bucket rebuilt (Will / MAIN / Sunday).
- Up to seven `crossword_history_stats` rows seeded or corrected for Will (Main, Monday–Sunday) that V8 never applied.

**Operational**

- Requires a production database query (`SELECT id, name, channel_id, discord_user_id FROM app_user;`) and a service log review before the midnight fix can be finalized.
- No breaking changes. No new dependencies.
