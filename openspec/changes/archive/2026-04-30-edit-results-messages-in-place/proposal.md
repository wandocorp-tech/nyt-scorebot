## Why

The results-channel scoreboards and the live status board both currently use a delete-then-repost pattern when their content changes (e.g. on `/flag`, `/duo`, mid-day re-submissions, or every status update). This creates avoidable Discord noise: each update produces a "new message" notification ping, the channel scrolls, and any user reactions or replies on the previous message are lost. The win-streak summary already edits in place (added in `add-crossword-win-streaks`); extending that behaviour to the rest of the bot's persistent messages keeps channels quiet and stable.

The status board has additional needs: today its "context message" header (e.g. "Conor submitted Wordle") is rendered above the table within the same message. After this change, that header is split out into a short-lived sibling message so users still receive a Discord notification on each submission, while the persistent status board message itself is just the table and is reused for the entire day, then reset to its empty state at midnight.

## What Changes

- `ResultsChannelService.refresh()` and `refreshGame(gameType)` edit the existing scoreboard message in place instead of deleting and reposting when a message ID is already known for that slot.
- The `WIN_STREAK_SUMMARY_SLOT` already edits in place; this change unifies the rest of the slots under the same pattern.
- Add a small private helper (or service utility) to centralise the "edit-or-post-with-fallback" logic so all the results-channel slots share one code path.
- `StatusChannelService.refresh(contextMessage)` is restructured:
  - The persistent status board message is created once per bot lifetime (or first refresh of the day) and **edited in place** for every subsequent update, never deleted-and-reposted.
  - The `contextMessage` is no longer rendered inside the status board. Instead, it is posted as its own short-lived message in the same channel (which fires the Discord notification) and then deleted automatically after a short delay (10 seconds).
- `StatusMessageBuilder` no longer accepts or renders a `contextMessage` parameter — the table is the entire content of the persistent status board.
- A new `StatusBoardMidnightJob` (`@Scheduled` cron `0 0 0 * * *`, same timezone as `WinStreakMidnightJob`) edits the persistent status board back to its empty/base state for the new day (all `⏳`, no one finished). The same persistent message is reused; no new message is posted at midnight.
- Both edit paths fall back to posting a new message if the tracked message ID is missing or the edit fails because the message no longer exists (e.g. someone deleted it manually).

## Capabilities

### New Capabilities
- `results-message-lifecycle`: Defines how the bot manages the lifecycle of long-lived channel messages (scoreboards, status board, win-streak summary): when they are posted, when they are edited in place, and when a fallback re-post is acceptable.

### Modified Capabilities
- None. (Existing `crossword-scoreboards` and `multi-channel-monitoring` specs describe what is rendered and which channels are monitored; they do not constrain the post-vs-edit behaviour.)

## Impact

- **Affected code**:
  - `nyt-scorebot-discord/.../discord/ResultsChannelService.java` — replace `deleteAndRepost` with edit-or-post helper.
  - `nyt-scorebot-discord/.../discord/StatusChannelService.java` — replace `deletePreviousMessage(...).then(postNewMessage(...))` with edit-or-post against a persistent message ID; add ephemeral-notification post-and-delete logic.
  - `nyt-scorebot-service/.../service/StatusMessageBuilder.java` — drop the `contextMessage` constructor parameter and the leading header lines from `build()`.
  - `nyt-scorebot-discord/.../discord/StatusBoardMidnightJob.java` — new `@Scheduled` job that edits the status board back to its empty state at 00:00.
  - `nyt-scorebot-discord/.../listener/MessageListener.java`, `FinishedCommandHandler.java`, `FlagReplyHelper.java` — callers continue to pass the context message; no change to call sites' signatures, but the parameter now drives the ephemeral notification rather than the table header.
  - `nyt-scorebot-discord/.../discord/ResultsChannelServiceTest.java`, `StatusChannelServiceTest.java`, `StatusMessageBuilderTest.java` — update mocks/assertions for the new flow; add a `StatusBoardMidnightJobTest`.
- **User-visible behaviour change**:
  - Updates to scoreboards no longer produce notifications or bubble messages to the bottom.
  - The status board now stays at its original channel position. A short-lived notification message ("Conor submitted Wordle", etc.) appears in the same channel for ~10 seconds and is then auto-deleted, preserving the notification ping while keeping the channel uncluttered.
  - The status board content resets to its empty/base state at midnight (Europe/London), reusing the same persistent message.
- **No DB, API, or dependency impact.**
- **Failure modes**: If an edit call fails because the message was deleted out-of-band, the helper falls back to posting a fresh message and updates the tracked ID — preserving today's behaviour as a safety net. If the ephemeral-notification delete fails, the message simply remains visible (degraded but harmless).
