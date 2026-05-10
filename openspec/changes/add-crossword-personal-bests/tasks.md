## 1. Domain & entity layer

- [x] 1.1 Add `MainCrosswordResult.isAssisted()` helper returning `true` when any of `duo`, `checkUsed`, or `lookups > 0` is set; add a unit test covering all flag combinations
- [x] 1.2 Add `BotText` constants for the new render strings: `SCOREBOARD_DELTA_AVG_HEADER` (`"Δ avg"`), `SCOREBOARD_PB_PREFIX` (`"PB:"`), `MSG_PB_BROKEN_FORMAT`, `MSG_PB_BROKEN_DOW_FORMAT`, `STATS_FOOTNOTE_ASSISTED_EXCLUDED` (`"(%d assisted excluded)"`)
- [x] 1.3 Add `PbDayOfWeek` (or similar) helper that maps `Optional<DayOfWeek>` ↔ a non-null sentinel string used in the DB layer (sentinel `"__ALL__"` for Mini/Midi rows; weekday name for Main rows)

## 2. Persistence

- [x] 2.1 Create `PersonalBest` JPA entity in `nyt-scorebot-database` with fields `id`, `user`, `gameType`, `dayOfWeek` (non-null sentinel string), `bestSeconds`, `bestDate`, `source`, and unique constraint `(user_id, game_type, day_of_week)`
- [x] 2.2 Add `PersonalBestSource` enum with values `MANUAL`, `COMPUTED` (stored as STRING)
- [x] 2.3 Create `PersonalBestRepository` with `findByUserAndGameTypeAndDayOfWeek(User, GameType, String)` and `existsBySource(PersonalBestSource)` query methods
- [x] 2.4 Add the `personal_best` entity/repository to JaCoCo exclusions in `pom.xml`
- [x] 2.5 Verify the table is created on a fresh H2 DB with `mvn test -Dtest=ScoreboardRepositoryTest` (smoke check that the schema generates cleanly) — done via `FlywayMigrationTest` + new `V6__personal_best.sql` migration; design.md updated to reflect that Flyway is in use.

## 3. Repository read paths for clean averages

- [x] 3.1 Add `ScoreboardRepository.findCleanMainSecondsBeforeDate(User user, DayOfWeek dow, LocalDate today)` returning `List<Integer>` of `totalSeconds` from clean Main results strictly before `today` matching the DoW
- [x] 3.2 Add `ScoreboardRepository.findCleanSecondsBeforeDate(User user, GameType gameType, LocalDate today)` returning `List<Integer>` from clean Mini/Midi results strictly before `today` (no DoW filter)
- [x] 3.3 Add tests in `ScoreboardRepositoryTest` covering: empty result, mix of clean and assisted, DoW filtering for Main, exclusion of today, exclusion of `duo=true`, exclusion of `checkUsed=true`, exclusion of `lookups>0`

## 4. PersonalBestService

- [x] 4.1 Create `PersonalBestService` in `nyt-scorebot-service` with method `recompute(User, GameType, LocalDate puzzleDate, int totalSeconds, boolean isClean) -> PbUpdateOutcome`
- [x] 4.2 Define `PbUpdateOutcome` sealed type with subtypes `NoChange` and `NewPb(Integer priorSeconds /* nullable */, int newSeconds, GameType gameType, Optional<DayOfWeek> dayOfWeek)`
- [x] 4.3 Implement the recompute decision tree per design D2: skip when not clean; insert when no row; replace when strictly faster; transition `MANUAL → COMPUTED` on improvement
- [x] 4.4 Add separate `seedFromHistory(User, GameType, Optional<DayOfWeek>, int seconds, LocalDate date)` method used by the backfill that does NOT return `NewPb` outcomes (always returns `NoChange`-equivalent)
- [x] 4.5 Unit tests covering: first-ever insert, strictly faster computed update, equal time no-update, slower no-update, assisted skipped, manual preserved against slower clean, manual replaced by faster clean (transitions to computed), Main DoW partitioning (Saturday update doesn't touch Tuesday row), Mini/Midi single-row behaviour

## 5. Backfill runner

- [x] 5.1 Create `PersonalBestBackfillRunner` (`ApplicationRunner`) in `nyt-scorebot-app` (or `-service` depending on where other runners live)
- [x] 5.2 Guard with `repo.existsBySource(COMPUTED)` — skip entirely when any computed row exists
- [x] 5.3 Stream all scoreboards in chronological order, group by `(user, gameType, dow)`, find the clean minimum, and call `seedFromHistory(...)` for each group
- [x] 5.4 Ensure the runner does NOT trigger any channel messages (uses `seedFromHistory`, not `recompute`)
- [x] 5.5 Tests covering: empty DB no-op, populated history produces correct rows, manual rows preserved, second invocation is a no-op (computed row exists), assisted-only history produces no rows

## 6. Save-path integration

- [x] 6.1 Modify `ScoreboardService.save(...)` (or whatever the entry method is) to call `PersonalBestService.recompute(...)` after a successful crossword result save, only for Mini / Midi / Main game types
- [x] 6.2 Thread the resulting `PbUpdateOutcome` through `SaveOutcome` (or a wrapping result type) so the listener can react
- [x] 6.3 Ensure non-crossword saves (Wordle, Connections, Strands) bypass the recompute entirely
- [x] 6.4 Update `ScoreboardServiceTest` to assert recompute is invoked once per Mini/Midi/Main save and not invoked for emoji-game saves

## 7. PB-break announcement

- [x] 7.1 In the relevant listener (`MessageListener` or its handler that posts the per-game scoreboard refresh), after handling the scoreboard render, inspect the save outcome and post a follow-up message when `PbUpdateOutcome` is `NewPb`
- [x] 7.2 Build the message text using `BotText.MSG_PB_BROKEN_FORMAT` for Mini/Midi and `BotText.MSG_PB_BROKEN_DOW_FORMAT` for Main, formatting time via the existing `m:ss` / `h:mm:ss` helper
- [x] 7.3 Omit the "(was M:SS)" segment when `priorSeconds` is null (first-ever PB)
- [x] 7.4 Listener tests cover: NoChange outcome posts no extra message; NewPb with prior posts the full message including "(was …)"; first-ever NewPb omits the prior segment; Main NewPb includes the day-of-week token

## 8. Renderer integration

- [x] 8.1 Extend `GameComparisonScoreboard` (or pass via the renderer's signature) with a per-render lookup providing each player's prior-clean average and current PB for the relevant `(game, dow)` pair, both as `OptionalInt` (seconds) values
- [x] 8.2 In `ScoreboardRenderer.renderTwoPlayer(...)` and `renderSinglePlayer(...)`, append the new rows between the score/flags rows and the outcome/streak row, only for crossword game types
- [x] 8.3 Implement the empty-history rule per design D5: blank-pad an individual player's column when only one side has history; omit all three new rows entirely when neither side does
- [x] 8.4 Implement signed-delta formatting `±M:SS` using ASCII `+` / `-` only (no glyphs)
- [x] 8.5 Wire the lookup data through `ScoreboardService` (or wherever `renderAll(...)` is called) to populate per-player avg + PB for the rendered date
- [x] 8.6 Add `ScoreboardRendererTest` snapshots for: Main two-player both-have-history, Main two-player one-side-empty, Main two-player both-empty (no new rows), Main single-player with history, Mini two-player with history, Midi two-player with history, assisted today still renders against avg/PB normally
- [x] 8.7 Assert max line width ≤ 33 cols on every rendered line in every snapshot test

## 9. Stats integration

- [x] 9.1 Modify `CrosswordStatsService` to skip Main results matching `isAssisted()` from `played`, `totalSec`, `bestSec`, and DoW total accumulators; track `excludedAssistedCount` per `(user, game)`
- [x] 9.2 Add `excludedAssistedCount` field to `CrosswordStatsReport.UserGameStats`
- [x] 9.3 Verify win attribution counters in `CrosswordStatsService` are unchanged by the new exclusion (existing duo/check/lookups handling remains)
- [x] 9.4 Update `CrosswordStatsServiceTest` to cover: assisted Main excluded from avg/best, wins still attributed under existing rules, excluded count populated correctly per player, Mini/Midi unaffected
- [x] 9.5 Modify `CrosswordStatsReportBuilder.appendGameSummary(...)` to render the `(N assisted excluded)` footnote line under each player's row when `excludedAssistedCount > 0`
- [x] 9.6 Update `CrosswordStatsReportBuilderTest` snapshots to cover footnote-present and footnote-absent cases for Main, and to verify Mini/Midi never render a footnote

## 10. Documentation & polish

- [x] 10.1 Update `README.md` (or `docs/`) to mention the new `personal_best` table, the manual-seed SQL pattern, and the PB-break message
- [x] 10.2 Add a short developer note in `docs/` (or a comment block on `PersonalBestService`) explaining the manual-vs-computed precedence rule
- [x] 10.3 Run the full unit suite (`mvn test -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'`) and verify JaCoCo coverage check passes
- [ ] 10.4 Manual verification on a dev DB: insert a manual seed row, restart, confirm the renderer reads it and the celebratory message fires when beaten by a clean save
