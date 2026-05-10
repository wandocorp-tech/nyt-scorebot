# Crossword personal bests

Per-user crossword PB records live in the `personal_best` table. There is one row per
`(user, game_type, day_of_week)` combination:

- **Mini & Midi** are not partitioned by weekday — the `day_of_week` column uses the
  sentinel value defined in `PbDayOfWeek.ALL_DAYS_SENTINEL` (currently `"ALL"`).
- **Main** is partitioned per weekday (`MONDAY` … `SUNDAY`) so PBs and prior averages
  are tracked at the per-difficulty granularity that NYT puzzles use.

Each row carries a `source` of either `COMPUTED` (set by the bot from a clean save or
by the initial-launch backfill runner) or `MANUAL` (hand-seeded via SQL).

## Manual-vs-computed precedence

`PersonalBestService` enforces this rule on every recompute:

| New clean save | Existing PB source | Result                                                   |
|---             |---                 |---                                                       |
| equal or slower | `MANUAL`          | preserved — no change                                    |
| equal or slower | `COMPUTED`        | preserved — no change                                    |
| strictly faster | `MANUAL`          | replaced; row transitions to `COMPUTED`                  |
| strictly faster | `COMPUTED`        | replaced; remains `COMPUTED`                             |

Assisted Main results (`MainCrosswordResult.isAssisted()` — duo / lookups / check)
**never** qualify as a PB, are excluded from the prior-clean average, and do not
trigger a PB-break announcement.

The bot itself never inserts `MANUAL` rows; that source value is reserved for
operator-managed seed data.

## Manual seed SQL pattern

Run once against the live DB after the schema migration has been applied. The
`@Enumerated(STRING)` mapping means enum values are stored as their uppercase
Java names (`'MANUAL'`, `'MAIN_CROSSWORD'`, `'MONDAY'`, etc.).

```sql
INSERT INTO personal_best (user_id, game_type, day_of_week, best_seconds, best_date, source)
VALUES
  ((SELECT id FROM app_user WHERE name = 'Player1'), 'MAIN_CROSSWORD', 'MONDAY',  300, NULL, 'MANUAL'),
  ((SELECT id FROM app_user WHERE name = 'Player1'), 'MAIN_CROSSWORD', 'TUESDAY', 379, NULL, 'MANUAL');
  -- … one row per (player, weekday)
```

`best_date` is nullable on manual rows because the original PB date is generally
unknown. Mini and Midi seed rows (if needed) use the sentinel:

```sql
INSERT INTO personal_best (user_id, game_type, day_of_week, best_seconds, best_date, source)
VALUES
  ((SELECT id FROM app_user WHERE name = 'Player1'), 'MINI_CROSSWORD', 'ALL', 25, NULL, 'MANUAL');
```

## Renderer & announcement behaviour

- Crossword scoreboards render `Δ avg`, signed delta vs prior-clean average
  (`±M:SS`, ASCII signs), and `PB:M:SS` rows below the time/flags rows. A player
  with no history has those cells blanked; when neither player has any history the
  three rows are omitted entirely.
- When a clean save breaks the player's PB, a celebratory message is posted to the
  same channel naming the player, game, new time, and prior PB (or "first ever"
  when none existed).
- The periodic stats reports exclude assisted Main results from `played`, `avg`,
  `best`, and DoW totals, and append a `(N assisted excluded)` footnote per
  player-row when `N > 0`. Win attribution is unchanged.
