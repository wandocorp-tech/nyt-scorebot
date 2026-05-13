## 1. Schema: Flyway V6 — `crossword_history_stats` table

- [x] 1.1 Create `nyt-scorebot-database/src/main/resources/db/migration/V6__crossword_history_stats.sql` defining the table with columns `id BIGINT IDENTITY PK`, `user_id BIGINT NOT NULL` (FK → `user.id`), `game_type VARCHAR(16) NOT NULL`, `day_of_week TINYINT NOT NULL` (0 sentinel for Mini/Midi, 1-7 for Main), `sample_count INT NOT NULL DEFAULT 0`, `sum_seconds BIGINT NOT NULL DEFAULT 0`, `pb_seconds INT NULL`.
- [x] 1.2 Add a unique index `ux_crossword_history_stats_bucket` on `(user_id, game_type, day_of_week)`.
- [x] 1.3 Add a `CHECK` constraint enforcing `game_type IN ('MINI', 'MIDI', 'MAIN')` and `day_of_week BETWEEN 0 AND 7`.
- [x] 1.4 Verify the migration runs cleanly against an empty H2 DB locally (`mvn -pl nyt-scorebot-database test` or `mvn spring-boot:run` against a fresh `data/` directory).

## 2. Schema: Flyway V7 — backfill from existing `scoreboard` rows

- [x] 2.1 Create `V7__backfill_crossword_history_stats.sql` with three `INSERT ... SELECT ... GROUP BY` statements:
  - Mini: `GROUP BY user_id`, sentinel `day_of_week = 0`, aggregating `mini_total_seconds` from rows where the Mini result is present.
  - Midi: same pattern for Midi.
  - Main: `GROUP BY user_id, DAY_OF_WEEK(date)`, aggregating only rows where `main_total_seconds IS NOT NULL` AND `main_check_used = false OR NULL` AND `(main_lookups IS NULL OR main_lookups = 0)` AND `main_duo = false OR NULL`.
- [x] 2.2 Use exact column names from the V4-normalised schema (inspect `V4__normalize_game_results.sql` and the current entities to get the names right).

## 3. Schema: Flyway V8 — manual Main PB seed

- [x] 3.1 Create `V8__seed_main_crossword_pbs.sql` with one `MERGE INTO crossword_history_stats ... USING (SELECT u.id ...) ...` statement per supplied `(player, day_of_week, pb_seconds)` triple, using `LEAST(COALESCE(dst.pb_seconds, src.pb_seconds), src.pb_seconds)` on `WHEN MATCHED` so a faster backfilled PB is never regressed.
- [x] 3.2 Insert `TODO` placeholder rows in V8 and add a top-of-file comment instructing the maintainer to fill in real values before merging. Ask the maintainer for the values during implementation.

## 4. Entity & repository

- [x] 4.1 Add `CrosswordHistoryStat` JPA entity in `nyt-scorebot-database` mapping the table with `@Entity`, `@Table`, `@Id`, columns annotated, and `equals`/`hashCode` on the surrogate id.
- [x] 4.2 Add `CrosswordHistoryStatRepository extends JpaRepository<CrosswordHistoryStat, Long>` with finder `Optional<CrosswordHistoryStat> findByUserIdAndGameTypeAndDayOfWeek(Long, String, byte)`.
- [x] 4.3 Add a custom upsert method on the repository (either a `@Modifying @Query` JPQL/native MERGE statement, or a default method that does `findOrInsert + update`). The upsert must be safe under concurrent writes — favour the native H2 `MERGE` form.

## 5. Service: `CrosswordHistoryService`

- [x] 5.1 Define `enum CrosswordGame { MINI, MIDI, MAIN }` and `record CrosswordHistoryStats(OptionalInt avgSeconds, OptionalInt pbSeconds)` in `nyt-scorebot-service`.
- [x] 5.2 Create `CrosswordHistoryService` with a constructor-injected `CrosswordHistoryStatRepository`.
- [x] 5.3 Implement `getStats(User user, CrosswordGame game, Optional<DayOfWeek> weekday)`:
  - For Mini/Midi, require `weekday.isEmpty()`; for Main, require `weekday.isPresent()` (throw `IllegalArgumentException` otherwise).
  - Lookup the row via the repository finder; map to `CrosswordHistoryStats` (avg = `Math.round((double) sum / count)` when `count > 0`, else empty; pb = `pb_seconds` when non-null, else empty).
- [x] 5.4 Implement `recordSubmission(User user, CrosswordGame game, LocalDate date, int totalSeconds, boolean checkUsed, int lookups, boolean duo)`:
  - For Main, return early (no-op) when `checkUsed || lookups > 0 || duo`.
  - Compute bucket key `(user, game, dayOfWeekOrZero)`.
  - Call the repository upsert with `+1` count, `+totalSeconds` sum, `LEAST(pb, totalSeconds)`.
- [x] 5.5 Add unit tests: empty stats → empty `OptionalInt`s, single write (avg == pb == that value), multi-write averaging including rounding, Main weekday bucketing, Main no-op for assisted/duo days, and that two `recordSubmission` calls in the same transaction produce the correct accumulated row.

## 6. `ScoreboardService` integration

- [x] 6.1 Inject `CrosswordHistoryService` into `ScoreboardService`.
- [x] 6.2 After persisting a `Scoreboard` row, for each newly-added Mini/Midi/Main result, call `crosswordHistoryService.recordSubmission(...)` inside the same transaction.
- [x] 6.3 Update existing `ScoreboardService` unit tests to verify the call is made with the right arguments (and is *not* made for non-crossword results or for duplicate-rejected submissions).

## 8. Renderer changes: Mini, Midi

- [x] 8.1 Update `MiniCrosswordScoreboard` to inject `CrosswordHistoryService` and call `getStats(user, MINI, Optional.empty())` for each player after rendering the existing rows.
- [x] 8.2 Append `avg` and `pb` rows using `ScoreboardLayout`, rendering `-` when the corresponding `OptionalInt` is empty.
- [x] 8.3 Update `MiniCrosswordScoreboard` tests to assert the new rows render correctly (including `-` empty state and weekday query arg).
- [x] 8.4 Repeat 8.1–8.3 for `MidiCrosswordScoreboard` with `CrosswordGame.MIDI`.

## 9. Renderer changes: Main

- [x] 9.1 Change the Main header from `Main - <date>` to `<DayOfWeek> - <date>` (use `crosswordDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)`).
- [x] 9.2 Switch the check flag glyph from `✓` to `✅` (with a `BotText` constant).
- [x] 9.4 Inject `CrosswordHistoryService` and call `getStats(user, MAIN, Optional.of(crosswordDate.getDayOfWeek()))` for each player.
- [x] 9.5 Append `avg` and `pb` rows using `ScoreboardLayout` (rendering `-` for empty results).
- [x] 9.6 Update existing `MainCrosswordScoreboard` tests:
  - Replace `Main - <date>` header assertions with `<DayOfWeek> - <date>`.
  - Replace `✓` assertions with `✅`.
  - Add tests for the new `avg`/`pb` rows including weekday filtering and clean-only filtering.

## 10. `BotText` constants

- [x] 10.1 Add `BotText` constants for the `avg` row label, the `pb` row label, the `-` empty-cell placeholder, and the new ✅ check glyph.
- [x] 10.2 Update any existing references to the old `✓` glyph to use the new constant.

## 11. Build, test, coverage

- [x] 11.1 Run `mvn test -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` and ensure all unit + Flyway integration tests pass.
- [x] 11.2 Run `mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` and ensure the JaCoCo 80% instruction + branch threshold is met.

## 12. Documentation

- [x] 12.1 Update `README.md` documenting: avg/pb rows on crossword scoreboards; Main header uses day-of-week; stats persisted in `crossword_history_stats`.
- [x] 12.2 No changes required to `docs/designs/main.txt` — it remains the source design.

## 13. Follow-up: crossword centre-divider alignment

- [x] 13.1 Add an opt-in crossword layout path so Mini, Midi, and Main name rows render with a centre `|` aligned to the avg/pb rows.
- [x] 13.2 Render crossword score rows with the same centre `|` and a `+` divider between the name and score rows.
- [x] 13.3 Preserve the existing Wordle, Connections, and Strands emoji-grid layout.
- [x] 13.4 Update renderer tests and OpenSpec artifacts to document the corrected alignment.
