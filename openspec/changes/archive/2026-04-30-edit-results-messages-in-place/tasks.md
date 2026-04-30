## 1. Shared edit-or-post helper

- [x] 1.1 Introduce a small package-private helper in `nyt-scorebot-discord` (e.g. `MessageSlotWriter` or a private method on a shared base) exposing `Mono<Snowflake> editOrPost(Snowflake channelId, @Nullable Snowflake existingId, String content)` that:
  - calls `Message.edit(spec -> spec.setContent(content))` when `existingId != null`,
  - on edit error (or when `existingId == null`), calls `MessageChannel.createMessage(content)`,
  - returns the resulting message's `Snowflake` (the existing one if edit succeeded, the new one if posted).
- [x] 1.2 Unit-test the helper directly: edit-success path, edit-error → post fallback path, no-existing-id → post path. Mock `GatewayDiscordClient`, `Message`, and `MessageChannel`.

## 2. Migrate `ResultsChannelService` to edit-in-place

- [x] 2.1 Replace `deleteAndRepost(...)` invocations in `refresh()` and `refreshGame(...)` with calls to the shared helper. Update `postedMessageIds` from the helper's returned `Snowflake`.
- [x] 2.2 Migrate `postOrEditWinStreakSummary(...)` and `editSummaryMessage(...)` to use the same helper, removing the now-duplicated edit logic.
- [x] 2.3 Remove the now-unused `deleteAndRepost(...)` method and any helpers that exist only to support delete-then-repost.
- [x] 2.4 Update `ResultsChannelServiceTest` to assert on `Message.edit(...)` (not `Message.delete()` + `createMessage(...)`) for the in-day refresh paths. Keep at least one test for the first-post path that asserts on `createMessage(...)`. Add a test for the edit-failure → fallback-post path.

## 3. Strip context message from `StatusMessageBuilder`

- [x] 3.1 Remove the `contextMessage` constructor parameter from `StatusMessageBuilder`. Update `build()` to start directly with the code-block opening (no leading prose, no leading newlines).
- [x] 3.2 Update `StatusMessageBuilderTest` to drop the contextMessage argument and assert that the rendered output starts with the table's code-block opening.

## 4. Migrate `StatusChannelService` to edit-in-place + ephemeral notifications

- [x] 4.1 Replace the `deletePreviousMessage(...).then(postNewMessage(...))` chain in `refresh(contextMessage)` with a call to the shared helper for the persistent status board. Update `lastMessageId` from the helper's returned `Snowflake`.
- [x] 4.2 After the persistent board edit, post the `contextMessage` as its own message in the same channel and chain `Mono.delay(Duration.ofSeconds(10))` then `Message::delete` (`onErrorResume` to swallow + log warn). Define the 10s default as a constant (e.g. `NOTIFICATION_TTL = Duration.ofSeconds(10)`) — no new property required.
- [x] 4.3 If `contextMessage` is null or blank, skip the ephemeral notification post (defensive — current callers always supply one, but guard against future mis-use).
- [x] 4.4 Remove the now-unused `deletePreviousMessage(...)` method and the `postNewMessage(...)` helper if no longer needed elsewhere.
- [x] 4.5 Update `StatusChannelServiceTest` to cover:
  - first-refresh creates the persistent board (createMessage),
  - subsequent refresh edits the persistent board in place,
  - edit failure on the persistent board falls back to a new post and updates `lastMessageId`,
  - ephemeral notification is posted then deleted after the configured delay (use `VirtualTimeScheduler` or an injected `Scheduler` to advance virtual time),
  - blank/null context message skips the ephemeral post.

## 5. Midnight reset of the status board

- [x] 5.1 Create `StatusBoardMidnightJob` (`@Component` in `nyt-scorebot-discord`) with `@Scheduled(cron = "0 0 0 * * *", zone = "${discord.timezone:Europe/London}")`. Inject `StatusChannelService` and call a new package-private method (e.g. `resetForNewDay()`) that builds an empty-state status table and edits the persistent message in place via the shared helper. Skip the edit if `lastMessageId` is null.
- [x] 5.2 Implement `StatusChannelService.resetForNewDay()`: build the empty status table by passing an empty `List<Scoreboard>` (or equivalent) to `StatusMessageBuilder`, then call the shared helper with the existing tracked `lastMessageId`. Do not post an ephemeral notification on reset.
- [x] 5.3 Add `StatusBoardMidnightJobTest`: verifies that when `lastMessageId` is set, `StatusChannelService.resetForNewDay()` is invoked; when `lastMessageId` is null, the job is a no-op.
- [x] 5.4 Verify `@EnableScheduling` is already present on `NytScorebotApplication` (added by `add-crossword-win-streaks`); no further wiring required.

## 6. Verification

- [x] 6.1 Run `mvn test -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` and confirm all unit tests pass.
- [x] 6.2 Run `mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` and confirm JaCoCo coverage (≥80% instruction + branch) still holds.
- [x] 6.3 Manual smoke check on a dev Discord channel:
  - Trigger a `/flag` after both players have finished and confirm the affected scoreboard message edits in place (no new notification, position unchanged).
  - Submit a result and confirm the status board edits in place AND a short-lived "X submitted Y" message appears for ~10s and disappears.
  - Manually delete the win-streak summary message and trigger another refresh to confirm the fallback re-post works.
  - Wait through midnight (or temporarily adjust the cron) and confirm the persistent status board content resets to all-pending without producing a new message.
