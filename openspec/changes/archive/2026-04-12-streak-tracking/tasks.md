## 1. Database Layer — Streak Entity & Repository

- [x] 1.1 Create `Streak` JPA entity in `nyt-scorebot-database` with fields: `id` (Long, auto-generated PK), `user` (ManyToOne to User), `gameType` (String), `currentStreak` (int, default 0), `lastUpdatedDate` (LocalDate). Add unique constraint on (user_id, game_type).
- [x] 1.2 Create `StreakRepository` interface in `nyt-scorebot-database` extending `JpaRepository<Streak, Long>` with methods: `Optional<Streak> findByUserAndGameType(User user, String gameType)` and `List<Streak> findAllByUser(User user)`.

## 2. Service Layer — StreakService

- [x] 2.1 Create `StreakService` in `nyt-scorebot-service` with method `updateStreak(User user, GameResult result)` implementing: game type resolution from GameResult class, success determination (Wordle/Connections use `completed`, Strands always succeeds), lazy gap detection against `PuzzleCalendar.today()`, and increment/reset logic per spec.
- [x] 2.2 Add `setStreak(User user, String gameType, int value)` method to `StreakService` for the `/streak` command — finds or creates Streak record, sets `currentStreak` and `lastUpdatedDate = today`.
- [x] 2.3 Add `getStreaks(User user)` returning `Map<String, Integer>` (gameType → currentStreak) and `getStreak(User user, String gameType)` returning `int` to `StreakService`.
- [x] 2.4 Write unit tests for `StreakService`: consecutive-day increment, gap reset then success (streak = 1), gap reset then failure (streak = 0), first-ever submission, Strands always succeeds, Wordle failure resets. Use `FixedPuzzleCalendar` for deterministic dates.

## 3. Integration — Wire Streak Updates into ScoreboardService

- [x] 3.1 Inject `StreakService` into `ScoreboardService`. After a successful `SaveOutcome.SAVED` in `saveResult()`, call `streakService.updateStreak(user, result)` within the same transaction.
- [x] 3.2 Write unit tests verifying: streak is updated when save succeeds, streak is NOT updated when outcome is `ALREADY_SUBMITTED` or `WRONG_PUZZLE_NUMBER`.

## 4. Interface & Rendering — Streak Display in Emoji Scoreboards

- [x] 4.1 Add `default boolean usesStreakDisplay()` method to `GameComparisonScoreboard` returning `false`. Override to return `true` in `WordleScoreboard`, `ConnectionsScoreboard`, and `StrandsScoreboard`.
- [x] 4.2 Update `ScoreboardRenderer.renderTwoPlayer()` to accept streak data (`Map<String, Map<String, Integer>>` — playerName → gameType → streak). When `game.usesStreakDisplay()` is `true`, render a streak row (`🔥 Name1: X  |  Name2: Y`) instead of calling `buildResultMessage()`.
- [x] 4.3 Update `ResultsChannelService.refresh()` and `refreshGame()` to fetch streak data via `StreakService.getStreaks()` for both players and pass it to the renderer.
- [x] 4.4 Add `BotText` constants: `CMD_STREAK` ("streak"), `CMD_STREAK_DESCRIPTION`, `CMD_STREAK_GAME_OPTION` ("game"), `CMD_STREAK_GAME_OPTION_DESC`, `CMD_STREAK_VALUE_OPTION` ("streak"), `CMD_STREAK_VALUE_OPTION_DESC`, `MSG_STREAK_SET` ("✅ %s streak set to %d"), `SCOREBOARD_STREAK_ROW` ("🔥 %s: %d  |  %s: %d").
- [x] 4.5 Write unit tests for `ScoreboardRenderer`: emoji scoreboard renders streak row, crossword scoreboard still renders outcome row, streak row formatting with various streak values.

## 5. Slash Command — /streak Registration & Handling

- [x] 5.1 Register `/streak` command in `SlashCommandRegistrar` with two required options: `game` (string choice: Wordle, Connections, Strands) and `streak` (integer, min 0).
- [x] 5.2 Add `handleStreak()` method in `SlashCommandListener` that: extracts game and streak params, resolves user by Discord ID, validates non-negative value, calls `streakService.setStreak()`, and returns ephemeral reply. Handle user-not-found with `MSG_USER_NOT_FOUND`.
- [x] 5.3 Write unit tests for `/streak` command handling: successful set, user not found, negative value rejected.

## 6. Cleanup & Verification

- [x] 6.1 Remove or deprecate win-related BotText constants that are no longer used by emoji scoreboards (`SCOREBOARD_TIE`, `SCOREBOARD_WIN_WITH_DIFF`, `SCOREBOARD_WIN_NO_DIFF`). Keep them if still used by crossword scoreboards; otherwise remove.
- [x] 6.2 Run full unit test suite (`mvn test -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'`) and verify all tests pass.
- [x] 6.3 Run `mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` to confirm JaCoCo coverage ≥ 80% with new code included.
