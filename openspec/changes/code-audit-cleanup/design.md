## Context

A code audit of the nyt-scorebot project identified dead code, a data-corruption bug, naming inconsistencies, hardcoded values, and duplicated logic accumulated across multiple layers. The project is ~2,300 LOC (main) and is mature enough that cleaning up now — before adding new game types or features — prevents the issues from compounding.

The changes span six packages (`listener`, `service`, `service/scoreboard`, `entity`, `config`, and test utilities) but are largely independent of each other. No changes to the Discord protocol, database schema shape, or application behaviour (except the `StringListConverter` bug fix) are intended.

## Goals / Non-Goals

**Goals:**
- Remove all confirmed dead code (unused class, unused enum value, unused constants)
- Fix the `StringListConverter` empty-list corruption bug
- Extract duplicated emoji-row validation into a single shared utility
- Make the bot's timezone configurable via `application.properties`
- Tighten access modifiers on fields exposed unintentionally as package-private
- Centralise all display strings in `BotText`
- Resolve two naming inconsistencies (`dailyCrosswordResult` vs `MAIN`; `gameType()` return values)
- Improve test quality: extract shared test utilities, remove reflection hacks
- Remove unnecessary Spring Boot dependencies/configuration

**Non-Goals:**
- Changing the application's Discord behaviour or user-facing messages
- Database schema migrations or new JPA entities
- Adding new game types
- N-player support (the two-player assumption is accepted by design)
- Performance optimisation

## Decisions

### D1: Remove `SaveOutcome.WRONG_DATE` rather than implement it
The crossword parser already extracts the puzzle date, and `ScoreboardService.saveResult()` silently stores results against a computed date rather than rejecting mismatches. Implementing date validation would change observable behaviour (submissions would start failing). Removing the dead enum value and its associated `BotText` constant and test is the lower-risk path.

**Alternative considered:** Implement crossword date validation. Rejected because it changes user-facing behaviour and is out of scope for a cleanup task.

### D2: Rename `getDailyCrosswordResult()` → `getMainCrosswordResult()` to match `CrosswordType.MAIN`
The enum is the authoritative source of truth for game type names. Renaming the getter is a pure internal rename with no external impact.

**Alternative considered:** Rename `CrosswordType.MAIN` → `CrosswordType.DAILY`. Rejected because the existing spec uses `DAILY` loosely as a label but `MAIN` is more accurate (there are Mini, Midi, and Main crosswords — "daily" is ambiguous).

### D3: Keep `gameType()` returning uppercase strings, document the convention
`gameType()` values (`"WORDLE"`, `"CONNECTIONS"`, `"STRANDS"`) serve as internal map keys in `ResultsChannelService.postedMessageIds`. Changing them to title-case would break key identity if any persisted state relied on them. Since they are in-memory only, changing is safe — but the display labels (`BotText.GAME_LABEL_*`) and the map keys serve different purposes. Align them so `gameType()` returns the same string as the corresponding `BotText` label (`"Wordle"`, `"Connections"`, `"Strands"`).

**Alternative considered:** Keep uppercase strings, add separate display-label method. Rejected as over-engineering for a two-player bot with no external API.

### D4: Extract `EmojiGridUtils.isEmojiRow()` as a package-private utility in `service/scoreboard`
The three duplicate `isXxxEmojiRow()` methods differ only in the emoji codepoint set and expected row length. A shared static method with `Set<Integer> allowedCodepoints` and `int expectedCount` (-1 for variable-length) covers all cases.

### D5: Make timezone configurable with `Europe/London` as the default
The property `discord.timezone` with default `Europe/London` preserves existing behaviour while allowing other users to deploy for their region.

### D6: Remove `spring-boot-starter-web` and H2 console
No HTTP endpoints exist. The H2 console is a development convenience that exposes the production database to the web. The bot should not be running an HTTP server. Removing the dependency simplifies the security surface and reduces JAR size.

**Risk:** Removing `spring-boot-starter-web` may break the application if some Spring Boot autoconfiguration implicitly depends on it. This should be verified by running the full test suite and smoke test after removal.

### D7: Test infrastructure — extract to `testutil` package, remove reflection
- `FixedPuzzleCalendar` moves to `src/test/java/.../testutil/FixedPuzzleCalendar.java`
- `ResultsChannelServiceTest` reflection on `postedMessageIds` is replaced by a package-private `setPostedMessageId(String, Snowflake)` test-only setter on `ResultsChannelService`

## Risks / Trade-offs

- **StringListConverter fix** — Any existing `""` values in the production H2 database for `solve_order` columns will now deserialise as `[]` instead of `[""]`. This is the correct behaviour, but it is a change. The solve-order is used for display only in `ConnectionsResult.toString()` so the impact is cosmetic. No migration required.
- **Removing `spring-boot-starter-web`** — If any transitive dependency or autoconfiguration relies on web classes being on the classpath, the application will fail to start. Test thoroughly.
- **Renaming `getDailyCrosswordResult()`** — Any code (including tests) referencing the old method name must be updated. Compile errors will catch all call sites.
- **`gameType()` string change** — `postedMessageIds` in `ResultsChannelService` is in-memory only (cleared on restart), so changing the map key format has no persistent impact.

## Migration Plan

All changes are backward-compatible at the database level except the `StringListConverter` fix, which improves correctness. No Flyway/Liquibase migration is needed. Deployment is a drop-in JAR replacement. The new `discord.timezone` property defaults to `Europe/London`, so existing deployments with no `application.properties` override are unaffected.
