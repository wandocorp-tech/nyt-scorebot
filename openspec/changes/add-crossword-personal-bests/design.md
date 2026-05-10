## Context

The bot already persists every crossword result and computes head-to-head outcomes per day. The recently-shipped `crossword-stats-reports` change added period aggregates exposed via `/stats` and three scheduled jobs. What does not yet exist is a long-running, per-player view of "how am I doing today vs my own history": no average comparison on the daily scoreboard, no persistent personal best, and no way to celebrate breaking one. This change adds that layer on top of the existing data without disturbing the daily render pipeline, the win-attribution rules, or the win-streak machinery.

Two structural constraints shape the design:

1. **Render width budget is hard-capped at 33 columns** (`BotText.MAX_LINE_WIDTH`). The two-player Main scoreboard already uses `%15s     %s` (=25 cols base) for the score row; any new row must fit within the same envelope while accounting for the fact that some glyphs (`▼`, `▲`, ✅, ⏳) render 2-wide on Discord depending on client/font.
2. **Manual SQL seeding must survive bot restarts and recomputes.** The user wants to set a PB once, by hand, and have the bot honour it as the baseline going forward.

## Goals / Non-Goals

**Goals:**
- Show "Δ avg" and "PB" rows inline on every per-game crossword scoreboard, fitting within the 33-col budget without arrow-glyph width risk.
- Persist personal bests in a real table that can be hand-seeded with `INSERT … ON CONFLICT DO UPDATE` once.
- Honour manually-seeded rows: the bot only overwrites them when a faster *clean* result actually beats them.
- Track Main PB and Main avg per day-of-week (a Saturday PB is independent of a Monday PB).
- Exclude assisted (duo / lookups / check) results from Main avg + PB calculations everywhere — daily scoreboard rows, `/stats` reports, and PB recompute.
- Post a celebratory PB-break message as a separate follow-up to the results channel.
- Add a per-player "(N assisted excluded)" footnote to `/stats` Main rows.

**Non-Goals:**
- Touching win attribution. The existing `MiniCrosswordScoreboard` / `MidiCrosswordScoreboard` / `MainCrosswordScoreboard` `determineOutcome()` rules remain exactly as they are; duo still produces "no win" via existing logic, check/lookups still produce automatic loss.
- Supporting Wordle / Connections / Strands. Their scales are small integers and their averages are mushy; out of scope for v1.
- Streak personal-bests (longest-ever streak). Deferred per user.
- Caching avg/PB lookups across the request. The cardinality is tiny (≤ N players × 3 games × ≤ 7 DoWs) and recomputation per save is cheap.
- A migration of the existing in-progress `add-crossword-stats-reports` change. This proposal targets the post-archive shape of `crossword-stats-reports`.

## Decisions

### D1. `personal_best` schema: `(user_id, game_type, day_of_week)` with NULLable DoW

```
personal_best
─────────────
id              BIGINT  PK identity
user_id         BIGINT  FK → user (NOT NULL)
game_type       VARCHAR NOT NULL          -- enum-as-string
day_of_week     VARCHAR NULL              -- 'MONDAY' .. 'SUNDAY' for Main, NULL for Mini/Midi
best_seconds    INTEGER NOT NULL
best_date       DATE    NULL              -- NULL allowed for hand-seeded rows
source          VARCHAR NOT NULL DEFAULT 'computed'   -- 'manual' | 'computed'
unique (user_id, game_type, day_of_week)  -- treats NULL as a value (H2 + Postgres both do)
```

**Why a single table with NULL DoW for non-Main:** keeps the read path uniform — one repository method `findByUserAndGameTypeAndDayOfWeek(user, game, dow)` works for everything, with `dow` always set to `null` for Mini/Midi and to the puzzle's `LocalDate.getDayOfWeek()` for Main. Avoids two parallel tables.

**Alternative considered: separate `personal_best` and `personal_best_dow` tables.** Rejected because it doubles the repository surface and adds a switch in every reader for no real benefit — the NULL handling is one line.

**Why store `best_date` and allow it to be NULL:** computed rows always have a date; manual rows may not (the user is hand-picking a value). The renderer doesn't display the date, only the celebratory message does, and it can fall back to omitting the "set on YYYY-MM-DD" suffix when null.

**Why `source` instead of a boolean:** future-proofs for things like `'imported'` from an external archive, with no schema change.

### D2. Manual rows are never overwritten unless the new result is *strictly* faster *and* clean

The recompute path is:

```java
recompute(user, gameType, dow, candidateSeconds, candidateDate, candidateIsClean):
  if !candidateIsClean: return PbUpdateOutcome.NoChange
  PersonalBest existing = repo.findOne(user, gameType, dow)
  if existing == null:
    save(new PersonalBest(user, gameType, dow, candidateSeconds, candidateDate, COMPUTED))
    return PbUpdateOutcome.NewPb(prior=null)
  if candidateSeconds >= existing.bestSeconds: return NoChange
  // candidate is faster
  if existing.source == MANUAL:
    // Q4 answer (c): only overwrite manual when a clean faster time arrives.
    // candidate is already known clean here, so we proceed.
  existing.bestSeconds = candidate; existing.bestDate = candidateDate;
  existing.source = COMPUTED   // beating a manual seed transitions to computed ownership
  return PbUpdateOutcome.NewPb(prior=existing.previousValue)
```

This satisfies: (a) manual rows are sacred against assisted/slower attempts, (b) the bot eventually takes ownership when real clean play surpasses the seed, (c) the same code path serves both seeded and unseeded users.

### D3. Backfill on first launch

A `PersonalBestBackfillRunner` (`ApplicationRunner`, `@Order(2)` after the existing `block-until-disconnect` runner so it doesn't block startup) walks all `Scoreboard` rows in chronological order on launch when *and only when* the table contains zero `source='computed'` rows. It computes per-user / per-(game, dow) PBs from clean results and inserts/updates `source='computed'` rows. Manually-seeded rows are not touched (the recompute path's manual-vs-computed precedence rule applies). Idempotency is guaranteed by the "zero computed rows" guard plus the per-row "only update if strictly faster" check inside `recompute`.

**Alternative considered: Flyway migration only.** Rejected because the backfill is not just "create the table" — it's "walk all historical scoreboards and compute clean PBs", which is too logic-heavy for raw SQL. Flyway creates the empty table (`V6__personal_best.sql`) and the runner populates it.

### D4. Inline render: the "Δ avg" header line + signed-delta row + PB row

The two-player Main worst case fits like this within the 33-col budget:

```
 Main - 5/10/2026
 ---------------------------------         (33 cols)
            Conor  |  Will                 (existing)
 ---------------------------------
            12:34     11:02                (existing score row)
 ---------------------------------         (NEW separator)
              Δ avg                        (NEW header, single line, centered-ish)
            -2:15     +0:08                (NEW delta row)
            PB:9:47   PB:8:30              (NEW PB row, no space before colon)
 ---------------------------------
       🏆 Will wins! (1:32)                (existing outcome)
 ---------------------------------
```

Format strings:
- Δ avg header: `String.format("%" + (PLAYER_COL_WIDTH + 7) + "s", "Δ avg")`  → centered between the two columns.
- Delta row: `String.format("%" + PLAYER_COL_WIDTH + "s     %s", leftDeltaStr, rightDeltaStr)` where `leftDeltaStr` is `"-2:15"` / `"+0:08"` / `"+0:00"`.
- PB row: `String.format("%" + PLAYER_COL_WIDTH + "s     %s", "PB:" + leftPb, "PB:" + rightPb)`.

**No emoji**, **no arrows** — only ASCII `+` / `-` signs as the direction indicator. This is the Q7-A5 path.

For single-player layout the same rows render against `SINGLE_PLAYER_LINE_WIDTH = 17` using the existing right-aligned single-column pattern.

For Mini and Midi, the same rows are appended; `dow` argument to the lookup is `null`.

### D5. Empty-history handling: omit both rows for that player

If a player has zero prior clean results for the relevant `(game, dow)`, both the Δ avg and PB rows are omitted **for that column entirely** (rendered as empty padding to preserve the other player's column). If both players have no history, both new rows are omitted entirely (Q8-a). The existing outcome / streak row immediately follows the score/flags row in that case — same as today.

This keeps the zero-state silent rather than visually polluting it with `PB: -` placeholders.

### D6. Avg + PB sourcing

The renderer reads from two places:

| Datum                        | Source                                                            |
|------------------------------|-------------------------------------------------------------------|
| PB                           | `personal_best` table via `PersonalBestRepository.findOne(...)`   |
| Avg (for the delta)          | Recomputed live: `ScoreboardRepository.findCleanResultsForAvg(user, gameType, dow)` returning a list of `(date, totalSeconds)` for clean results strictly before today. The renderer computes the mean. |

Why not a separate `running_average` table? Avg is unbounded-history with no compaction needed, and the read happens at most once per scoreboard render. The query is an indexed range-scan filtered by user+game; well within latency budget on the Pi for the foreseeable corpus size.

The query excludes Main results where `duo = TRUE OR check_used = TRUE OR lookups > 0`. For Mini/Midi the filter is a no-op (no flag columns). Implemented as two repository methods (`findCleanMainSeconds(user, dow)`, `findCleanSeconds(user, gameType)`) to keep the JPQL legible.

### D7. PB-break announcement: separate follow-up message

After a successful save:

```
ScoreboardService.save(...)
   ├── persists Scoreboard row
   ├── PersonalBestService.recompute(user, gameType, dow, sec, date, isClean) → PbUpdateOutcome
   └── returns SaveResult(saveOutcome, pbUpdateOutcome)

MessageListener
   ├── posts the per-game scoreboard refresh as today
   └── if pbUpdateOutcome instanceof NewPb:
         channel.createMessage(BotText.format(MSG_PB_BROKEN, name, gameLabel, dowLabel, time, prior))
```

The PB message is a plain (non-code-block) channel message so it visually breaks from the table render. Format:

```
🏆 New PB! Conor's Main (Saturday): 12:34 (was 14:02)
```

For Mini/Midi, the `(Saturday)` segment is omitted. For first-ever PB (no prior), the `(was …)` segment is omitted.

PB-break is **only** announced for transitions from `prior < bestSeconds` (computed-row updates). Beating a manually-seeded value still announces — the user wants to know.

### D8. `/stats` integration: assisted exclusion + footnote

`CrosswordStatsService.compute(...)` is updated:

- For Main only, when accumulating `played`, `totalSec`, `bestSec` and DoW totals: skip the result when `MainCrosswordResult.isAssisted()`.
- Track `excludedAssistedCount` per `(user, game)` and bubble it into `UserGameStats`.
- Wins and forfeits are unchanged — the existing duo / check / lookups handling stays.

`CrosswordStatsReportBuilder.appendGameSummary(...)` adds, after each player row, a conditional footnote line:

```
 Main
---------------------------------
 Player  | Win | Avg     | Best
---------------------------------
 Conor   |  12 | 11:32   | 9:47
       (3 assisted excluded)
 Will    |   8 | 12:01   | 10:14
```

The footnote is omitted when `excludedAssistedCount == 0`. Footnote width is comfortably under 33 cols at typical magnitudes (`(N assisted excluded)` ≤ 22 chars).

### D9. `MainCrosswordResult.isAssisted()`

A trivial derived helper, not a column:

```java
public boolean isAssisted() {
    return Boolean.TRUE.equals(duo)
        || Boolean.TRUE.equals(checkUsed)
        || (lookups != null && lookups > 0);
}
```

Centralises the rule so `CrosswordStatsService`, `PersonalBestService`, and the avg-query paths all agree. The classifier intentionally aligns with the user's "track duo separately later" intent — when that lands, this one method changes and all callers update consistently.

## Risks / Trade-offs

- **Layout regression risk for the new rows** → Mitigation: snapshot tests in `ScoreboardRendererTest` for each game's two-player and single-player render with (a) no history, (b) history without PB break, (c) history with PB break candidate, (d) Main with assisted day in history (excluded). Width is asserted via `String.length()` against `MAX_LINE_WIDTH`.
- **Backfill correctness** → Mitigation: dedicated `PersonalBestBackfillRunnerTest` constructing a synthetic scoreboard history with known PBs (including assisted days that must be excluded) and asserting the resulting `personal_best` rows. Manual-row preservation case is tested explicitly.
- **NULL-as-unique-value semantics** → H2 (used for tests + dev) and Postgres treat `NULL` as distinct from every value in unique constraints, which is the *opposite* of what we want. Mitigation: replace the `day_of_week` column with a non-null sentinel `'__NONE__'` for Mini/Midi rows (or use an `int` with `-1`). Decision: use a non-null `day_of_week` column with `'__ALL__'` literal for Mini/Midi rows; map this in the entity layer so callers still pass `Optional<DayOfWeek>` without seeing the sentinel.
- **PB recompute on every save adds a write** → Cardinality is one row per save; the table is tiny. Acceptable.
- **`personal_best` schema drift if the user already seeded before this PR ships** → Mitigation: the user's seed values must use the final column names. The proposal documents the schema; the user controls the seed timing.
- **Avg query growing unbounded** → Sample size is real-world ≤ a few thousand rows per user per game even after years; a single indexed scan. If it ever becomes a hotspot, a `running_average` materialisation can be added without changing the renderer API.
- **PB-break message floods on backfill** → The backfill calls a separate `seedFromHistory(...)` path, **not** `recompute()`, so it never produces `PbUpdateOutcome.NewPb`. Only live saves announce.

## Migration Plan

1. Deploy with the new entity + Flyway migration `V6__personal_best.sql` (creates the empty table).
2. On first startup post-deploy, `PersonalBestBackfillRunner` populates `personal_best` from history (silent, no announcements).
3. Operator runs the one-time SQL seed for any hand-picked PBs (`INSERT ... ON CONFLICT (user_id, game_type, day_of_week) DO UPDATE SET best_seconds = EXCLUDED.best_seconds, source = 'MANUAL', best_date = NULL;`).
4. From the next save onward, the new rows render and PB-break announcements fire when triggered.

Rollback: revert the migration / drop the `personal_best` table and revert the renderer changes. No data on existing tables is altered.
