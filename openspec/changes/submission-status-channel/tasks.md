## 1. Constants

- [x] 1.1 Create `BotText` final class in `com.wandocorp.nytscorebot` with `public static final String` constants for all emoji and status indicators or messages used in the bot (✅, ⏳, ⚠️, ℹ️)
- [x] 1.2 Replace all inline emoji string literals in `MessageListener` with references to `BotEmoji` constants
- [x] 1.3 Replace all inline emoji string literals in `SlashCommandListener` with references to `BotEmoji` constants

## 2. Configuration

- [x] 2.1 Add an optional `statusChannelId` field to `DiscordChannelProperties` (top-level, alongside `token` and `channels`)
- [x] 2.2 Add `statusChannelId` to the example `application.yml` in README documentation

## 3. Scoreboard Query

- [x] 3.1 Add a method to `ScoreboardRepository` to fetch all `Scoreboard` entries for a given date (across all users)
- [x] 3.2 Add a `getTodayScoreboards()` method to `ScoreboardService` that returns all scoreboards for today along with their associated `User` names, ordered by player name

## 4. Status Channel Service

- [x] 4.1 Create `StatusChannelService` Spring `@Service` class in `com.wandocorp.nytscorebot.service`
- [x] 4.2 Inject `GatewayDiscordClient`, `DiscordChannelProperties`, and `ScoreboardService`; hold an `AtomicReference<Snowflake>` for the last posted message ID
- [x] 4.3 Implement `buildStatusTable()` private method: queries today's scoreboards, builds a monospace table with per-game ✅/⏳ cells and a trailing Status column showing `Finished` / `Complete` / `In Progress` / `No Submissions` per the priority rules; uses `BotEmoji` constants for the cell indicators
- [x] 4.4 Implement `refresh()` public method: if `statusChannelId` is absent, return immediately (no-op); otherwise delete the previous message (swallowing 404 errors), then post the new table; update the stored message ID
- [x] 4.5 Ensure `refresh()` is non-blocking (uses `.subscribe()` or returns a `Mono` chained reactively)

## 5. Status Message Listener

- [x] 5.1 Create `StatusMessageListener` Spring `@Component` in `com.wandocorp.nytscorebot.listener`
- [x] 5.2 Subscribe to `MessageCreateEvent`; if `statusChannelId` is absent, do nothing
- [x] 5.3 Identify the bot's own user ID via `GatewayDiscordClient.getSelfId()`

## 6. Trigger Refresh from Listeners

- [x] 6.1 Inject `StatusChannelService` into `MessageListener`; call `statusChannelService.refresh()` when `saveResult()` returns `SAVED`
- [x] 6.2 Inject `StatusChannelService` into `SlashCommandListener`; call `statusChannelService.refresh()` when `markComplete()` returns `MARKED_COMPLETE` or `ALREADY_COMPLETE`

## 7. Tests

- [x] 7.1 Unit-test `StatusChannelService.buildStatusTable()`: verify correct Status label for each of the four states (Finished/Complete/In Progress/No Submissions) and correct ✅/⏳ per game cell
- [x] 7.2 Unit-test `StatusChannelService.refresh()` no-op path when `statusChannelId` is not configured
- [x] 7.3 Unit-test `StatusMessageListener`: verify non-bot messages in the status channel trigger deletion, and bot messages do not
- [x] 7.4 Update `MessageListenerTest` to verify `statusChannelService.refresh()` is called on `SAVED` outcome
- [x] 7.5 Verify `mvn verify -Dtest='!com.wandocorp.nytscorebot.SmokeTest'` passes with ≥80% coverage
- [x] 7.6 Unit tests should achieve 80% coverage in jacoco
