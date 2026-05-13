## Context

The bot currently renders three daily crossword scoreboards (Mini, Midi, Main) into the results channel. Each scoreboard is built by a dedicated class in `nyt-scorebot-service` (`MiniCrosswordScoreboard`, `MidiCrosswordScoreboard`, `MainCrosswordScoreboard`) using string padding helpers and `BotText` constants. Today's scoreboards display:

```
 Main - 5/10/2026
---------------------------------
        William | Conor
----------------+----------------
          15:00 | 22:02
---------------------------------
    👫 🔍×2 ✓    ✓
---------------------------------
 🤝 Tie!
---------------------------------
```

`docs/designs/main.txt` proposes a redesigned layout that adds two new rows (`avg`, `pb`) and tightens the visual:

```
 Sunday - 5/10/2026
---------------------------------
        William | Conor
----------------+----------------
          15:00 | 22:02
---------------------------------
    👫 🔍×2 ✅    ✅
---------------------------------
 🤝 Tie!
---------------------------------
 avg |   1:15:30 | 31:22
-----+-----------+---------------
 pb  |     14:00 | 21:00
---------------------------------
```

Constraints we must respect:
- Discord renders code blocks in a monospace font but emojis often render as ~2 narrow-character widths; today the renderers count them as 1 char, which causes the centre divider to drift.
- The bot already persists every `Scoreboard` row keyed by `(user, date)` with embedded `MiniCrosswordResult`, `MidiCrosswordResult`, `MainCrosswordResult` — no schema change is needed to compute history.
- The H2 file DB on the Pi is small (years of data is still only thousands of rows). JPQL aggregation `AVG`/`MIN` over filtered results is well within budget; we can compute on demand each render.
- `crossword-stats-reports` (a separate, in-flight change) introduces `CrosswordStatsService` for *period* aggregation. This change is for *all-time* (or all-time-for-weekday) per-user stats embedded in the daily scoreboard. The two are intentionally separate so this change does not block on the stats-reports work.

## Goals / Non-Goals

**Goals:**
- Add `avg` and `pb` rows to all three daily crossword scoreboards in the results channel.
- For Main, filter `avg`/`pb` to the current weekday and to "clean" submissions only (no check, no lookups, no duo).
- Switch to ✅ for the check flag and to a day-of-week header for Main.
- Make all three crossword scoreboards visually consistent under one set of alignment rules: emoji-as-2-chars, divider pluses aligned, 1-char padding from the centre divider.
- Omit the Main flags row entirely when no flags are set on either side.
- Compute the new stats on demand from existing data — no schema change, no cache, no new scheduled job.

**Non-Goals:**
- Wordle / Connections / Strands scoreboards. They are out of scope and unchanged.
- The `/stats` slash command and `crossword-stats-reports` capability — unchanged.
- Win streak logic, status channel rendering, parsers, listeners, persistence — unchanged.
- Materialised caches for history. We compute on demand; if profiling shows it is slow, a cache can be added in a follow-up without changing the public-facing layout.
- Internationalisation of the day-of-week header (English only, matching the rest of `BotText`).

## Decisions

### Decision 1: Persist incrementally-maintained stats in a dedicated table

**Choice**: Add a new `crossword_history_stats` table managed by Flyway. One row per `(user_id, game_type, day_of_week)`. For Mini and Midi, `day_of_week` is `NULL` (one row per game per user). For Main, there is one row per weekday per user.

Columns:
| column | type | notes |
| --- | --- | --- |
| `id` | `BIGINT IDENTITY` | surrogate PK |
| `user_id` | `BIGINT NOT NULL` | FK → `user.id` |
| `game_type` | `VARCHAR(16) NOT NULL` | one of `MINI`, `MIDI`, `MAIN` (matches the existing `game_type` discriminator convention) |
| `day_of_week` | `TINYINT NULL` | `NULL` for Mini/Midi; 1-7 (`DayOfWeek.getValue()`) for Main |
| `sample_count` | `INT NOT NULL DEFAULT 0` | number of qualifying submissions counted into the row |
| `sum_seconds` | `BIGINT NOT NULL DEFAULT 0` | running sum, used to compute `avg = sum / count` |
| `pb_seconds` | `INT NULL` | best (lowest) qualifying time, or `NULL` if none yet |

Uniqueness: a unique index on `(user_id, game_type, day_of_week)` so each player has at most one row per bucket. H2 treats `NULL` as distinct in unique indexes by default; we sidestep this by storing `0` for Mini/Midi `day_of_week` (sentinel: 0 is not a valid `DayOfWeek` value) **OR** by enforcing uniqueness application-side. **Decision**: use a sentinel value of `0` for Mini/Midi to keep the unique index simple and portable; application code maps `Optional<DayOfWeek>` ↔ `0`/`1..7`.

The renderer reads stats from this table only — it never aggregates from `scoreboard` at render time. The `ScoreboardService` write path updates the relevant row inside the same transaction as the `Scoreboard` save.

**Alternatives considered**:
- **JPQL aggregation on demand from `scoreboard`.** Simpler (no new table, no write-path coupling) and well within H2's budget. Rejected per user direction in favour of an explicit cache table that is also seedable with manually-supplied PBs (see Decision 8).
- **One row per `(user, game_type)` with a JSON column for per-weekday Main buckets.** Rejected — defeats relational queries and complicates Flyway migrations.
- **Materialised view.** H2's view support is limited and non-portable; an explicit table with our own write path is simpler and testable.

**Rationale**: A persisted, incrementally-maintained table makes (a) stats reads trivially fast at render time, (b) the manual PB seed straightforward (one `UPSERT` per supplied value), and (c) the on-disk values inspectable via SQL for debugging.

### Decision 2: Incremental update strategy on each crossword save

**Choice**: When `ScoreboardService` persists a new crossword result, it calls `CrosswordHistoryService.recordSubmission(user, game, result)`. The service:

1. Determines whether the submission qualifies. For Mini/Midi every submission qualifies. For Main, it qualifies only when `checkUsed != true` AND `(lookups == null || lookups == 0)` AND `duo != true`. Non-qualifying Main submissions are a **no-op** — no row created, no counters incremented.
2. Computes the bucket key: `(user, game, dayOfWeekOrZero)`. For Main, `dayOfWeekOrZero = result.crosswordDate.getDayOfWeek().getValue()`. For Mini/Midi, `0`.
3. Performs an atomic upsert using a single SQL statement (H2 supports `MERGE INTO ... USING ... ON ... WHEN MATCHED THEN UPDATE WHEN NOT MATCHED THEN INSERT`):
   - On insert: `sample_count = 1, sum_seconds = newSeconds, pb_seconds = newSeconds`.
   - On update: `sample_count = sample_count + 1, sum_seconds = sum_seconds + newSeconds, pb_seconds = LEAST(pb_seconds, newSeconds)` (handle `NULL` `pb_seconds` from a seed-only row by treating `NULL` as `+infinity`, i.e., `COALESCE`).

The write runs in the same transaction as the `Scoreboard.save(...)`. If the save rolls back, the stats update rolls back too.

**Alternatives considered**:
- **Recompute the bucket from `scoreboard` on every save** (read all qualifying rows, recompute, write back). Correct but wasteful and racy under concurrency.
- **Maintain stats via DB triggers**. Not portable, hard to test, hides write semantics outside the application.

**Rationale**: Incremental upsert is O(1) per save, atomic, and correct under concurrent writes thanks to the unique index + `MERGE`.

### Decision 3: Backfill via Flyway (`V7`) on first deploy

**Choice**: Migration `V7__backfill_crossword_history_stats.sql` populates the table from existing `scoreboard` rows in pure SQL:

```sql
INSERT INTO crossword_history_stats (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
SELECT user_id, 'MINI', 0, COUNT(*), SUM(mini_total_seconds), MIN(mini_total_seconds)
FROM scoreboard WHERE mini_total_seconds IS NOT NULL GROUP BY user_id;
-- + analogous statements for MIDI and MAIN (Main: GROUP BY user_id, day_of_week_of(date) and WHERE not assisted/duo)
```

The exact column names will follow whatever the V4 normalised schema produced (the migration is hand-written against that schema). The migration runs only on first deploy of this change and is naturally idempotent because the table is empty before V7.

**Rationale**: Backfilling in Flyway keeps the deployment story atomic — schema, data, and seed all happen in one DB upgrade. There is no need for a separate "first run on startup" Java backfill path.

### Decision 4: Manual Main PB seed via Flyway (`V8`) using `MERGE` with `LEAST`

**Choice**: Migration `V8__seed_main_crossword_pbs.sql` is a hand-written, one-off SQL file. Each supplied `(user_discord_id, day_of_week, pb_seconds)` triple becomes a `MERGE` statement:

```sql
MERGE INTO crossword_history_stats AS dst
USING (SELECT u.id AS user_id, 'MAIN' AS game_type, :dow AS day_of_week, :pb AS pb_seconds
       FROM "user" u WHERE u.discord_user_id = :discord_id) AS src
ON dst.user_id = src.user_id AND dst.game_type = src.game_type AND dst.day_of_week = src.day_of_week
WHEN MATCHED THEN UPDATE SET pb_seconds = LEAST(COALESCE(dst.pb_seconds, src.pb_seconds), src.pb_seconds)
WHEN NOT MATCHED THEN INSERT (user_id, game_type, day_of_week, sample_count, sum_seconds, pb_seconds)
                       VALUES (src.user_id, src.game_type, src.day_of_week, 0, 0, src.pb_seconds);
```

The merge guarantees:
- If V7 already backfilled a faster PB, the seed does **not** regress it (`LEAST(existing, supplied)`).
- If no row exists yet for that `(user, weekday)` combination (e.g., the player has never submitted a clean Main on that weekday in the bot's history), a row is created with `sample_count = 0` and `sum_seconds = 0` so the `pb` cell renders correctly while the `avg` cell renders `-` until real submissions accumulate.

The migration file is committed with placeholder `TODO` rows; the maintainer fills in actual values before merging the PR.

**Alternatives considered**:
- **Separate Spring `CommandLineRunner` reading values from a properties file.** Couples startup to a config file the operator must remember to maintain; less reproducible across environments. Rejected.
- **Slash command `/seed-pb`.** Out of scope and overkill for a one-off.

**Rationale**: A versioned, reviewable SQL migration is the simplest correct way to land a one-off data change.

### Decision 5: One `CrosswordHistoryService` for read + write

**Choice**: A single `@Service` exposing:
```java
CrosswordHistoryStats getStats(User user, CrosswordGame game, Optional<DayOfWeek> weekday);
void recordSubmission(User user, CrosswordGame game, LocalDate date, int totalSeconds,
                      boolean checkUsed, int lookups, boolean duo);
```
The write path encapsulates the qualifying-submission rule for Main; the read path is a simple key lookup. `record CrosswordHistoryStats(OptionalInt avgSeconds, OptionalInt pbSeconds)` returns empty `OptionalInt`s when no row exists OR when `sample_count == 0` (avg) / `pb_seconds IS NULL` (pb).

**Rationale**: One class owns the qualifying rule, the bucket key derivation, and the upsert SQL — no risk of the read and write paths diverging.

### Decision 6: Empty-history rendering = `-` placeholder

**Choice**: When `OptionalInt` is empty (`sample_count == 0` for avg, `pb_seconds IS NULL` for pb), render the cell as `-` left-padded to the column width. The divider stays in place.

**Rationale**: Keeps the layout stable. After V8, seed-only rows will have `pb` set but `avg = -` until the first qualifying submission arrives.

### Decision 7: Centralise alignment in a `ScoreboardLayout` helper

**Choice**: Introduce one small helper class (in `nyt-scorebot-service`) that owns:
- `int displayWidth(String)` — counting emojis (and their `×N` suffixes) as 2 chars per emoji, ASCII as 1 char each.
- `String renderRow(String label, String left, String right)` — pads `left` and `right` so that each cell sits exactly 1 char from the centre `|`, computes the divider line of matching width, and (for the `avg` / `pb` rows) prefixes the label column with a `+` aligned to the centre divider.

All three crossword renderers call this helper. The Wordle / Connections / Strands renderers are not migrated by this change.

**Rationale**: Without one place to compute widths, the three renderers will drift apart and the alignment rules from `docs/designs/main.txt` will be re-implemented inconsistently.

### Decision 8: Day-of-week header for Main only

**Choice**: Replace `Main - <date>` with `<DayOfWeek> - <date>` (e.g., `Sunday - 5/10/2026`) so the avg/pb filtering is self-evident. Mini / Midi keep their existing `Mini - <date>` / `Midi - <date>` headers.

**Mini/Midi note**: The design doc shows them sharing one block (`Mini/Midi - 5/10/2026`). We keep them as **two separate scoreboards** (the existing structure) and treat the design doc's combined header as illustrative only. Rationale: the bot already has independent submission/forfeit semantics per game.

### Decision 9: Rounding of `avg`

**Choice**: Compute `avg = Math.round((double) sum_seconds / sample_count)` and format using the existing `H:MM:SS` / `M:SS` formatter.

## Risks / Trade-offs

- **[Risk]** Stats table can drift from `scoreboard` if writes happen outside `ScoreboardService` (e.g., a future bulk import). → **Mitigation**: All writes go through `ScoreboardService`; add a Javadoc warning on the `Scoreboard` entity / repo and an integration test that simulates a `recordSubmission` call after every save path.
- **[Risk]** `MERGE INTO ... USING ...` syntax is H2-specific; switching DBs in future would require rewriting V8 and the upsert. → **Mitigation**: Document the H2 dependency on the migration; we have no plans to switch DBs.
- **[Risk]** The V8 seed file is committed with placeholder values. Forgetting to fill them in would leave Main PBs only as whatever the V7 backfill produced. → **Mitigation**: V8 file fails the build with a clear `RAISE_APPLICATION_ERROR` (or `SELECT 1/0`) when placeholders are detected, OR the maintainer fills it before merge and PR review enforces it.
- **[Risk]** Sentinel value `0` for Mini/Midi `day_of_week` is a leaky abstraction. → **Mitigation**: Encapsulate the mapping in `CrosswordHistoryService`; nothing outside the service / repository sees the sentinel.
- **[Trade-off]** Storing `sum_seconds` as `BIGINT` is overkill for current data volumes but trivially future-proof; `INT` would also fit but `BIGINT` removes an entire failure mode.
- **[Trade-off]** "Clean only" for Main means a player who has never solved a Sunday Main without lookups will permanently see `-` for Sunday `pb` (until the V8 seed lands or they solve one cleanly). This is the intended behaviour and the user's stated preference.
- **[Risk]** Switching `✓` → `✅` changes the column width in the flags row. → **Mitigation**: Centralised `ScoreboardLayout.displayWidth` plus a renderer test that asserts the exact rendered string with both flag glyphs.

## Migration Plan

1. Land the spec, entity, repository, service, layout helper, renderer changes, and Flyway migrations V6, V7, V8 (with maintainer-supplied PB values) in one PR.
2. On deploy, Flyway runs V6 (DDL), V7 (backfill from existing `scoreboard` rows), and V8 (manual Main PB seed). After deploy, the next results-channel render will show fully-populated `avg` / `pb` rows.
3. Rollback: drop V8 / V7 / V6 in reverse order via a hand-written down-migration **OR** revert the deploy and drop the table manually. There is no production data outside the new table that needs undoing.

## Open Questions

- **Q1**: What is the format the maintainer will supply Main PBs in (CSV, inline list, etc.)? **Answer (deferred to implementation)**: Implementer asks the maintainer for `(player_name OR discord_user_id, day_of_week, pb_in_M:SS)` triples and converts to SQL `MERGE` statements in V8.
- **Q2**: Should the avg row ever render with sub-second precision? **Answer**: No — round to whole seconds (Decision 9).
