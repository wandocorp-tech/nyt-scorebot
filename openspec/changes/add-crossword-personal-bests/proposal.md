## Why

Daily crossword scoreboards in the results channel show today's head-to-head outcome and the running win streak, and the `/stats` reports show period-aggregated averages. There is nothing tying the two together: when a player posts a result, they cannot see how today's time compares to their long-term average, what their personal best is, or whether they just broke it. The bot already has every data point needed — it just doesn't surface them.

Adding inline "vs avg" deltas, persistent personal-best tracking (with a manually seedable seed value), and a celebratory PB-break message turns each daily post into a richer self-comparison moment. Excluding assisted (duo / lookups / check) results from average and PB calculations keeps those numbers honest.

## What Changes

- **Add a `personal_best` table and entity** keyed by `(user_id, game_type, day_of_week)` where `day_of_week` is `NULL` for Mini and Midi (single PB per game) and a `DayOfWeek` value for Main (one PB per weekday). Each row stores `best_seconds`, `best_date`, and a `source` column (`'manual'` or `'computed'`) so manually-seeded values can be distinguished. Manual rows can be inserted/updated by hand via SQL once and are then updated by the bot only when a faster *clean* result beats them.
- **Add a `PersonalBestService`** that, after each successful crossword result save (Mini, Midi, Main), recomputes the relevant PB row from the live data using only clean results, and returns whether a new PB was set.
- **Add inline "vs avg" + "PB" rows** to every per-game crossword scoreboard rendered by `ScoreboardRenderer`. The two new rows appear directly below the score / flags rows, before the existing outcome / streak row. For Mini and Midi the values are the player's overall avg/PB. For Main the values are the player's *day-of-week* avg/PB matched to the puzzle's date. The avg row uses signed deltas with a single shared "Δ avg" header line above to avoid arrow-glyph width risk and stay within the 33-column budget. The PB row is always shown when the player has at least one prior clean result for that game (and that DoW, for Main).
- **Add a celebratory PB-break message** posted as a separate follow-up message to the results channel whenever a save results in a new computed PB. The message includes the player name, game label, new time, prior best, and (for Main) the day of the week.
- **Exclude assisted results from average + PB calculations.** A Main result is "assisted" when any of `duo`, `checkUsed`, or `lookups > 0` is set. Mini and Midi have no flags so are never assisted. The win-attribution logic in `MiniCrosswordScoreboard` / `MidiCrosswordScoreboard` / `MainCrosswordScoreboard` is **unchanged**.
- **Surface excluded count as a footnote** on `/stats` reports: each player's row in the per-game summary table is followed (when applicable) by a footnote line `(N assisted excluded)` showing how many days were dropped from that player's avg/PB sample.
- **Backfill PBs on first launch.** A startup runner walks all historical scoreboards once, populates the `personal_best` table from the live data using the clean-result rule, and then exits. Manually-seeded rows (`source='manual'`) are not overwritten by the backfill.
- **Empty-history layout rule.** When a player has no prior clean result for a game (or for Main, no prior clean result on that day-of-week), both the "vs avg" and "PB" rows are omitted entirely for that player's column on that day's scoreboard. Layout falls back to the current row set.

## Capabilities

### New Capabilities

- `crossword-personal-bests`: Persistent per-user personal-best tracking for crossword games (Mini, Midi single PB; Main per-day-of-week PB), seedable manually via SQL, recomputed automatically from clean results, surfaced inline on daily scoreboards, and announced via a celebratory message when broken.

### Modified Capabilities

- `crossword-scoreboards`: Add the inline "Δ avg" delta row and the "PB" row to every per-game crossword scoreboard, between the score/flags rows and the existing outcome/streak row. Define empty-history fallback (rows omitted) and Main per-DoW data sourcing.
- `crossword-stats-reports`: Exclude assisted Main results from `avg` and `best` calculations (wins logic unchanged); add per-player `(N assisted excluded)` footnote when N > 0.

## Impact

- **New entity + repository**: `PersonalBest` (in `nyt-scorebot-database`) with composite uniqueness on `(user_id, game_type, day_of_week)`, plus `PersonalBestRepository`. The `personal_best` table is added to `JaCoCo` exclusions for entity-only / repository-only files.
- **New service**: `PersonalBestService` (in `nyt-scorebot-service`) recomputes PBs from clean results, returns a `PbUpdateOutcome` describing whether a new PB was set and what the prior value was.
- **Modified service**: `ScoreboardService.save(...)` calls `PersonalBestService.recompute(...)` after a successful crossword result save and threads the resulting outcome through to the listener so a follow-up message can be posted.
- **Modified renderer**: `ScoreboardRenderer` (and `GameComparisonScoreboard` API) gains access to a per-player avg/PB lookup. New rows appended only for crossword game types (Mini, Midi, Main). Wordle / Connections / Strands scoreboards are unchanged.
- **Modified service**: `CrosswordStatsService` filters Main avg/PB sample by `isAssisted()`; tracks excluded count per (user, game) and exposes it on `UserGameStats`. Win attribution unchanged.
- **Modified builder**: `CrosswordStatsReportBuilder` renders the `(N assisted excluded)` footnote when `excludedAssistedCount > 0`.
- **New listener glue**: After save, `MessageListener` posts the PB-break follow-up message into the results channel using the new `BotText` template.
- **`MainCrosswordResult`**: Add a derived `isAssisted()` helper method (no schema change).
- **New startup runner**: `PersonalBestBackfillRunner` (`ApplicationRunner`) populates the table once on launch when empty (computed rows only); idempotent, respects existing manual rows.
- **`BotText`**: New constants for the "Δ avg" header, the PB row format, the PB-break celebratory message template, and the assisted-excluded footnote.
- **JaCoCo**: New service is subject to the existing 80% coverage threshold; new entity/repository are added to exclusions.
- **Tests**: New unit tests cover `PersonalBestService` (clean-result filter, manual-row preservation, Main per-DoW partitioning, PB-break detection), `PersonalBestBackfillRunner` (idempotence, manual-row preservation), `ScoreboardRenderer` (new rows present when history exists, omitted when not, Main uses DoW data), `CrosswordStatsService` (assisted exclusion from avg/PB, wins unchanged, excluded count populated), `CrosswordStatsReportBuilder` (footnote rendering), and `MessageListener` (PB-break message posted on save).
- **No breaking changes** to existing scoreboards, win-streak logic, `/stats` win attribution, or any DB column on existing tables.
