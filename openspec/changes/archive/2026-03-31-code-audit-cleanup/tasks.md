## 1. Dead Code Removal

- [x] 1.1 Delete `service/scoreboard/PlayerColumn.java` (unused record, zero references)
- [x] 1.2 Remove `WRONG_DATE` from `SaveOutcome` enum
- [x] 1.3 Remove `MSG_WRONG_DATE` from `BotText`
- [x] 1.4 Remove the `WRONG_DATE` branch from `MessageListener.replyForOutcome()` switch
- [x] 1.5 Remove the `WRONG_DATE` test case from `MessageListenerTest`
- [x] 1.6 Remove `GREEN`, `YELLOW`, `ORANGE`, `RED` constants from `BotText`
- [x] 1.7 Update `StatusMessageBuilderTest` to reference `BotText.CHECK_MARK` / `BotText.CROSS_MARK` instead of the removed colour constants

## 2. Bug Fix — StringListConverter

- [x] 2.1 Fix `StringListConverter.convertToEntityAttribute()` to return `Collections.emptyList()` when the database value is `null` or blank
- [x] 2.2 Add a test case to `StringListConverterTest` covering the empty-string round-trip (`""` → empty list)

## 3. Access Modifiers & Encapsulation

- [x] 3.1 Change `MessageListener.channelPersonMap` and `channelUserIdMap` from package-private to `private`
- [x] 3.2 Verify `MessageListenerTest` still compiles (tests access these via public methods, not the fields directly; if not, fix accordingly)

## 4. BotText Centralisation

- [x] 4.1 Add `GAME_LABEL_GENERIC = "game"` to `BotText`
- [x] 4.2 Replace the hardcoded `return "game"` on line 140 of `MessageListener.gameLabel()` with `return BotText.GAME_LABEL_GENERIC`

## 5. Naming Alignment

- [x] 5.1 Rename `CrosswordType.MAIN` → no change (keep MAIN); rename `Scoreboard.getDailyCrosswordResult()` → `getMainCrosswordResult()` and `setDailyCrosswordResult()` → `setMainCrosswordResult()`
- [x] 5.2 Update all call sites: `ScoreboardService` (3 references), `StatusMessageBuilder` (1 reference), `MessageListener` (1 reference), `StringListConverter` if applicable
- [x] 5.3 Update all tests referencing `getDailyCrosswordResult()` / `setDailyCrosswordResult()`
- [x] 5.4 Change `WordleScoreboard.gameType()` to return `BotText.GAME_LABEL_WORDLE` (`"Wordle"`)
- [x] 5.5 Change `ConnectionsScoreboard.gameType()` to return `BotText.GAME_LABEL_CONNECTIONS` (`"Connections"`)
- [x] 5.6 Change `StrandsScoreboard.gameType()` to return `BotText.GAME_LABEL_STRANDS` (`"Strands"`)
- [x] 5.7 Update `ScoreboardRendererTest` and any test asserting on gameType strings

## 6. Extract Emoji Row Validation Utility

- [x] 6.1 Create `service/scoreboard/EmojiGridUtils.java` with a static `isEmojiRow(String line, Set<Integer> allowedCodepoints, int expectedCount)` method (pass `-1` for variable-length)
- [x] 6.2 Replace `WordleScoreboard.isWordleEmojiRow()` with a call to `EmojiGridUtils.isEmojiRow(line, WORDLE_EMOJI_CODEPOINTS, 5)`
- [x] 6.3 Replace `ConnectionsScoreboard.isConnectionsEmojiRow()` with a call to `EmojiGridUtils.isEmojiRow(line, CONNECTIONS_EMOJI_CODEPOINTS, 4)`
- [x] 6.4 Replace `StrandsScoreboard.isStrandsEmojiRow()` with a call to `EmojiGridUtils.isEmojiRow(line, STRANDS_EMOJI_CODEPOINTS, -1)`
- [x] 6.5 Add unit tests for `EmojiGridUtils`: blank line, correct count, wrong count, disallowed codepoint, variable-length pass, variable-length empty

## 7. Extract ScoreboardRenderer Column Width Constant

- [x] 7.1 Add `private static final int PLAYER_COL_WIDTH = 15` to `ScoreboardRenderer`
- [x] 7.2 Replace the two hardcoded `15` values in format strings with the constant

## 8. Configurable Timezone

- [x] 8.1 Add `discord.timezone=Europe/London` to `application.properties`
- [x] 8.2 Inject the timezone in `PuzzleCalendar` via `@Value("${discord.timezone:Europe/London}")` and replace the hardcoded `ZoneId.of("Europe/London")`
- [x] 8.3 Update `PuzzleCalendarTest` if the zone is now injected differently (e.g., use a constructor parameter)

## 9. Remove Unnecessary Dependencies

- [x] 9.1 Remove `spring-boot-starter-web` from `pom.xml`
- [x] 9.2 Remove `spring.h2.console.enabled=true` from `application.properties`
- [x] 9.3 Remove `spring.jpa.database-platform=org.hibernate.dialect.H2Dialect` from `application.properties`
- [x] 9.4 Run `mvn test -Dtest='!com.wandocorp.nytscorebot.SmokeTest'` to confirm nothing breaks

## 10. Test Infrastructure

- [x] 10.1 Create `src/test/java/com/wandocorp/nytscorebot/testutil/FixedPuzzleCalendar.java` as a package-accessible subclass of `PuzzleCalendar` that pins `today()` to a fixed date
- [x] 10.2 Replace the private inner `FixedPuzzleCalendar` class in `ScoreboardServiceTest` with an import of the new shared class
- [x] 10.3 Add a package-private `setPostedMessageId(String gameType, Snowflake id)` method to `ResultsChannelService` (annotate with a comment marking it for test use)
- [x] 10.4 Replace the `Field.setAccessible(true)` reflection block in `ResultsChannelServiceTest` with a call to `setPostedMessageId()`

## 11. Verify & Cleanup

- [x] 11.1 Run `mvn test -Dtest='!com.wandocorp.nytscorebot.SmokeTest'` — all tests must pass
- [x] 11.2 Run `mvn verify -Dtest='!com.wandocorp.nytscorebot.SmokeTest'` — JaCoCo coverage must remain ≥ 80%
- [x] 11.3 Run `mvn clean package -DskipTests` and confirm the JAR builds cleanly
