## 1. Model Layer

- [x] 1.1 Create `MainCrosswordResult` class extending `CrosswordResult` with `duo` (Boolean), `lookups` (Integer), and `checkUsed` (Boolean) fields. Annotate as `@Embeddable`.
- [x] 1.2 Update `Scoreboard` entity: change `dailyCrosswordResult` field type from `CrosswordResult` to `MainCrosswordResult`. Add `@AttributeOverrides` for the three new columns with `daily_` prefix.
- [x] 1.3 Update `ScoreboardService.saveResult()` to wrap parsed `CrosswordResult` (MAIN type) into a `MainCrosswordResult` before setting it on the scoreboard.
- [x] 1.4 Add `SetFlagOutcome` enum with values: `FLAG_SET`, `FLAG_CLEARED`, `NO_MAIN_CROSSWORD`, `NO_SCOREBOARD_FOR_DATE`, `USER_NOT_FOUND`, `INVALID_VALUE`.

## 2. Flag Service Methods

- [x] 2.1 Add `ScoreboardService.toggleDuo(String discordUserId, LocalDate date)` returning `SetFlagOutcome`. Toggle `duo` on today's `MainCrosswordResult`; return appropriate outcome for missing scoreboard/crossword/user.
- [x] 2.2 Add `ScoreboardService.setLookups(String discordUserId, LocalDate date, int count)` returning `SetFlagOutcome`. Set `lookups` on today's `MainCrosswordResult`; return `INVALID_VALUE` for negative input; set to null if count is 0.
- [x] 2.3 Add `ScoreboardService.toggleCheck(String discordUserId, LocalDate date)` returning `SetFlagOutcome`. Toggle `checkUsed` on today's `MainCrosswordResult`.

## 3. Slash Commands

- [x] 3.1 Add `BotText` constants for the three new command names (`CMD_DUO`, `CMD_LOOKUPS`, `CMD_CHECK`) and reply messages for each `SetFlagOutcome`.
- [x] 3.2 Register `/duo`, `/lookups`, and `/check` slash commands in `DiscordConfig` (or wherever commands are registered). `/lookups` takes a required integer option.
- [x] 3.3 Add `/duo` handler in `SlashCommandListener`: resolve user, call `toggleDuo()`, map outcome to ephemeral reply, refresh channels on success.
- [x] 3.4 Add `/lookups` handler in `SlashCommandListener`: extract integer argument, call `setLookups()`, map outcome to ephemeral reply, refresh channels on success.
- [x] 3.5 Add `/check` handler in `SlashCommandListener`: resolve user, call `toggleCheck()`, map outcome to ephemeral reply, refresh channels on success.

## 4. Crossword Scoreboards

- [x] 4.1 Create `MiniCrosswordScoreboard` implementing `GameComparisonScoreboard` with `@Order(5)`. `scoreLabel()` returns time string, `emojiGridRows()` returns empty list, `header()` uses crossword date, `determineOutcome()` compares `totalSeconds`.
- [x] 4.2 Create `MidiCrosswordScoreboard` implementing `GameComparisonScoreboard` with `@Order(6)`. Same pattern as Mini.
- [x] 4.3 Create `MainCrosswordScoreboard` implementing `GameComparisonScoreboard` with `@Order(7)`. Same time comparison as Mini/Midi, but `emojiGridRows()` returns a single flags-indicator row (👫, 🔍×N, ✓) if any flags are set, or empty list if no flags.
- [x] 4.4 Add `BotText` constants for crossword scoreboard header labels and flag indicator strings.
- [x] 4.5 Verify crosswords render within BotText.MAX_LINE_WIDTH (33). Adjust leadingSpaces/baseGap or abbreviate labels if any lines exceed the width; add renderer unit tests to assert line lengths.

## 5. Tests

- [x] 5.1 Unit tests for `MainCrosswordResult` — verify flag fields are settable and default to null.
- [x] 5.2 Unit tests for `ScoreboardService` flag methods (`toggleDuo`, `setLookups`, `toggleCheck`) — cover all `SetFlagOutcome` branches including toggle on/off, missing crossword, missing scoreboard, untracked user, and negative lookups.
- [x] 5.3 Unit tests for `SlashCommandListener` — verify `/duo`, `/lookups`, `/check` dispatch to service methods and return correct ephemeral replies for each outcome.
- [x] 5.4 Unit tests for `MiniCrosswordScoreboard` — verify `hasResult`, `header` (date format), `scoreLabel` (time string), `determineOutcome` (faster wins, tie, waiting).
- [x] 5.5 Unit tests for `MidiCrosswordScoreboard` — same coverage as Mini.
- [x] 5.6 Unit tests for `MainCrosswordScoreboard` — same time comparison tests plus flags row rendering (no flags → empty, mixed flags → correct indicators).
- [x] 5.7 Verify `mvn verify` passes with ≥80% coverage threshold.
