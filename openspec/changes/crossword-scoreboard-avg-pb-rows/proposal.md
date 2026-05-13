## Why

Today the daily crossword scoreboards (Mini, Midi, Main) only show *that day's* solve times and outcome. Players have no in-line context for how today's time compares to their own history — they have to wait for the weekly/monthly recap or run `/stats`. Adding a small `avg` and `pb` (personal best) row directly under each daily scoreboard turns every result into an immediate "how did I do vs. my norm?" moment without leaving the channel.

A redesigned layout (per `docs/designs/main.txt`) also tightens alignment, switches to ✅ for the check flag, treats all emojis as 2 character widths, and uses the day-of-week as the Main scoreboard header — making the Main avg/pb (which is filtered to that weekday) self-explanatory.

## What Changes

- **Add `avg` and `pb` rows** to all three crossword daily scoreboards (Mini, Midi, Main) shown beneath the existing time / flags / outcome rows.
  - Mini and Midi: `avg` is the player's all-time mean `totalSeconds` across submitted days for that game; `pb` is the player's all-time minimum `totalSeconds` for that game.
  - Main: `avg` and `pb` are filtered to **the same day of the week** as today (e.g., Sunday's scoreboard shows the Sunday-only average and PB), and **only "clean" submissions** (no `checkUsed`, `lookups == 0`, and `duo == false`) contribute. Days with assistance or duo are excluded from both `avg` and `pb`.
  - Times are rendered using the existing `H:MM:SS` / `M:SS` formatting (no leading zero on the most-significant unit).
  - When a player has no qualifying history (e.g., first-ever Main submission with no clean prior Sundays), their `avg` / `pb` cell renders as `-`.
  - Today's submission **is included** in the calculation (matches the user's expectation that "today's time appears in the average it's compared to").
- **Change Main scoreboard header** to the day-of-week and date (e.g., `Sunday - 5/10/2026`) instead of `Main - 5/10/2026`. Mini and Midi headers are unchanged (`Mini - <date>`, `Midi - <date>`, etc.).
- **Switch the check-flag glyph from `✓` to `✅`** in the Main flags row.
- **Width-normalise emojis to 2 characters** when computing column padding so the centre divider stays aligned regardless of how the host renders narrow vs. wide glyphs.
- **Align the `avg` / `pb` divider pluses** with the existing name/time bar divider, and ensure all data cells sit exactly 1 character from the centre `|`.
- **Omit the Main flags (emoji) row** entirely when neither player has any flag set (today this row may render even when empty). Mini and Midi never had a flags row.
- **Add a new persisted table `crossword_history_stats`** that records, per `(user, game_type, day_of_week)`, an incrementally-maintained `sample_count`, `sum_seconds`, and `pb_seconds`. For Mini and Midi the `day_of_week` column is `NULL`. For Main there is one row per weekday per user (so up to seven rows per Main player). The renderer reads this table for every scoreboard render — no aggregation is computed at render time.
- **Add a `CrosswordHistoryService`** (in `nyt-scorebot-service`) that exposes a read API (`getStats(User, CrosswordGame, Optional<DayOfWeek>) → CrosswordHistoryStats`) and a write API invoked by `ScoreboardService` whenever a crossword result is persisted. The write API increments `sample_count` and `sum_seconds` and updates `pb_seconds = MIN(current, new)` for the appropriate row, applying the "clean only" rule for Main (rows with `checkUsed`, `lookups > 0`, or `duo` do **not** contribute to Main stats and do **not** create a Main row).
- **Add Flyway migration `V6__crossword_history_stats.sql`** that creates the table and a unique index on `(user_id, game_type, day_of_week)` (with `day_of_week` participating in the index even when `NULL`, using a sentinel value or H2-supported `NULLS NOT DISTINCT` semantics — see design.md).
- **Add Flyway migration `V7__backfill_crossword_history_stats.sql`** that backfills the table from existing `scoreboard` rows: for Mini/Midi, aggregate every submitted day per user; for Main, aggregate clean (`checkUsed = false`/null AND `lookups = 0`/null AND `duo = false`/null) submissions grouped by `(user, day_of_week)`. PBs become `MIN(totalSeconds)`, sums become `SUM(totalSeconds)`, counts become `COUNT(*)`.
- **Add Flyway migration `V8__seed_main_crossword_pbs.sql`** — a one-off, hand-written migration that seeds Main `pb_seconds` per `(user, day_of_week)` from values the maintainer supplies. The migration SHALL use `MERGE`/`UPSERT` semantics: if no row exists for that `(user, game_type=MAIN, day_of_week)` yet, insert one with `sample_count = 0`, `sum_seconds = 0`, and `pb_seconds = <supplied>`; if a row already exists (created by the V7 backfill), update `pb_seconds = LEAST(existing, supplied)` so a faster historical PB recorded by the bot is never accidentally regressed. The supplied values will be filled in during implementation; the migration template is committed with `TODO` placeholders to be replaced before the change is merged.
- **No new commands, no new channels, no new scheduled jobs.** The `/stats` command and the `crossword-stats-reports` capability are unchanged.

## Capabilities

### New Capabilities

<!-- None — all changes are spec deltas to the existing crossword-scoreboards capability. -->

### Modified Capabilities

- `crossword-scoreboards`: Adds `avg` and `pb` rows to all three crossword daily scoreboards; changes the Main header to day-of-week; switches the check-flag glyph to ✅; tightens alignment rules (emoji-as-2-chars, divider alignment, 1-char padding from centre); requires the Main flags row to be omitted when no flags are set.

## Impact

- **Modified renderers**: `MiniCrosswordScoreboard`, `MidiCrosswordScoreboard`, `MainCrosswordScoreboard` (in `nyt-scorebot-service`) — each gains an `avg`/`pb` row, and Main gains the day-of-week header plus the omitted-empty-flags-row behaviour. Shared alignment helpers (likely a small `ScoreboardLayout` utility or extension of the existing formatting helpers) absorb the emoji-width and divider-alignment rules so all three renderers stay consistent.
- **New entity & repository**: `CrosswordHistoryStat` JPA entity (`nyt-scorebot-database`) backed by the `crossword_history_stats` table, plus `CrosswordHistoryStatRepository` exposing read-by-key and atomic upsert/increment operations.
- **New service**: `CrosswordHistoryService` in `nyt-scorebot-service` exposing both a read API (`getStats(User, CrosswordGame, Optional<DayOfWeek>)`) and a write API (`recordSubmission(User, CrosswordGame, MainCrosswordResult-like)`) called by `ScoreboardService` after a crossword result is persisted. For Main, the write API short-circuits (records nothing) when `checkUsed`, `lookups > 0`, or `duo` is set.
- **Three new Flyway migrations**: `V6__crossword_history_stats.sql` (DDL + unique index), `V7__backfill_crossword_history_stats.sql` (idempotent backfill from existing `scoreboard` rows), and `V8__seed_main_crossword_pbs.sql` (one-off manual Main PB seed using `MERGE`/`UPSERT` so it neither duplicates rows nor regresses faster backfilled PBs).
- **`ScoreboardService` integration**: After saving a `Scoreboard` row containing a Mini/Midi/Main crossword result, invoke `CrosswordHistoryService.recordSubmission(...)` for each newly-added crossword result. Use the same transaction as the scoreboard save so backfill counts and live writes never diverge.
- **`BotText`**: New constants for the `avg` and `pb` row labels, the `-` placeholder, and the `✅` check glyph.
- **No changes** to existing entities (other than additive new ones), the `Scoreboard` schema, parsers, listeners, the status channel, the `/stats` command, win-streak logic, or scheduled jobs.
- **JaCoCo**: New service is subject to the existing 80% instruction + branch coverage threshold. The new entity / repository follow the existing `model/*` and `repository/*` exclusion convention.
- **Tests**:
  - Unit tests for `CrosswordHistoryService` covering: empty stats → `-` cells, single-day write (avg == pb == that day), multi-write averaging including rounding, Main day-of-week bucketing, Main exclusion of any submission with `checkUsed`, `lookups > 0`, or `duo == true` (no row created, no counters updated), idempotent re-write semantics under retry, and the read returning the same value just written within a transaction.
  - `@DataJpaTest` (or equivalent) coverage of the V7 backfill and V8 seed migrations: seeded H2 with mixed clean/assisted/duo Main rows + Mini/Midi rows, runs Flyway, asserts row counts, `pb_seconds`, `sum_seconds`, and `sample_count` values; asserts V8 takes `LEAST(existing, supplied)` and never duplicates rows.
  - Renderer tests asserting the exact rendered layout (header, time row, optional flags row, outcome row, `avg` row, `pb` row, divider pluses aligned, 1-char padding from centre, ✅ glyph for check, day-of-week header for Main, flags-row omitted when empty).
  - Update existing `MainCrosswordScoreboard` tests that previously asserted the `Main - <date>` header and the `✓` glyph; remove any assertion that relies on a flags row being present when no flags are set.
