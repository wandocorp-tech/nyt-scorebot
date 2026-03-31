## Why

As the project matures, a code audit identified a set of dead code, inconsistencies, a data-corruption bug, and hardcoded values that have accumulated across the parser, service, entity, listener, and test layers. Addressing these now—before the codebase grows further—keeps the code easy to extend and reason about.

## What Changes

- **Delete** `PlayerColumn.java` — unused record with zero references
- **Fix** `StringListConverter` round-trip bug — empty list corrupts to `[""]` on round-trip
- **Remove** dead `SaveOutcome.WRONG_DATE` — enum value never returned; resolve the corresponding dead `BotText.MSG_WRONG_DATE` constant and test coverage accordingly
- **Remove** unused `BotText` colour constants (`GREEN`, `YELLOW`, `ORANGE`, `RED`) — only used in tests; collapse duplication with `CHECK_MARK`/`CROSS_MARK`
- **Tighten** `MessageListener` field access modifiers — `channelPersonMap` and `channelUserIdMap` are package-private, should be `private`
- **Extract** shared emoji-row validation utility — `isWordleEmojiRow`, `isConnectionsEmojiRow`, `isStrandsEmojiRow` are near-identical; extract to a shared utility
- **Make timezone configurable** — `Europe/London` hardcoded in `PuzzleCalendar`; move to `application.properties`
- **Remove** redundant `H2Dialect` property — auto-detected by Spring Boot
- **Evaluate removing** `spring-boot-starter-web` — no REST endpoints; only powers H2 console
- **Move** fallback `"game"` string to `BotText` — `MessageListener.gameLabel()` hardcodes a fallback string
- **Align naming** — `getDailyCrosswordResult()` vs `CrosswordType.MAIN`; resolve to one convention
- **Align** `gameType()` return strings with `BotText` labels (`"WORDLE"` vs `"Wordle"`)
- **Extract** `"%15s"` format constant in `ScoreboardRenderer`
- **Extract** `FixedPuzzleCalendar` to a shared test utility class
- **Remove** reflection hack in `ResultsChannelServiceTest`

## Capabilities

### New Capabilities
- `emoji-row-validation`: Shared utility for validating emoji grid rows across game scoreboard implementations

### Modified Capabilities
- `user-scoreboard-persistence`: `StringListConverter` bug fix changes round-trip behaviour for empty solve-order lists; the fix makes it correct but is a behaviour change for any existing `""` values in the database

## Impact

- **Code removed:** `PlayerColumn.java`, dead enum value, dead BotText constants
- **Bug fix:** `StringListConverter.convertToEntityAttribute("")` now returns empty list
- **Configuration:** new `discord.timezone` property in `application.properties`
- **Dependencies:** potentially removing `spring-boot-starter-web` from `pom.xml`
- **Tests:** `FixedPuzzleCalendar` moves to a shared test utility; reflection-based state injection replaced with behavioural assertion
- **No API or Discord protocol changes**
