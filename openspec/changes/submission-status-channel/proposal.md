## Why

Players currently have no at-a-glance way to see who has submitted which puzzles for the day without scrolling back through individual game channels. A dedicated status channel displaying a live, auto-updating table gives everyone a clear, persistent overview of the day's submission progress without cluttering game channels.

## What Changes

- A new `statusChannelId` configuration property added to `DiscordChannelProperties` identifying the dedicated status channel.
- On every successful result submission (or `/finished` command), the bot deletes its previous status message in that channel and posts an updated one.
- The status message contains a formatted table showing each player's submission state for every game type (Wordle, Connections, Strands, Mini, Midi, Daily Crossword) and whether they've marked themselves as `/finished`.
- Non-bot messages posted to the status channel are deleted immediately.
- All emoji literals used across the bot are centralised in a new `BotEmoji` constants class.

## Capabilities

### New Capabilities
- `submission-status-channel`: A dedicated, bot-managed Discord channel that displays and continuously updates a table of all players' daily submission statuses. The bot does not enforce read only access for users. it will be enabled manually

### Modified Capabilities
- `multi-channel-monitoring`: The channel configuration now includes an optional `statusChannelId` field; the `MessageListener` must also watch the status channel to delete non-bot messages posted there.

## Impact

- **Config**: `application.yml` / `DiscordChannelProperties` gains a top-level `statusChannelId` field (optional; feature disabled if absent).
- **New class**: `BotEmoji` constants class consolidating all emoji strings used in replies and the status table.
- **New class**: `StatusChannelService` responsible for building the status table and managing the pinned status message (delete + repost).
- **New class**: `StatusMessageListener` subscribing to `MessageCreateEvent` to delete non-bot messages in the status channel.
- **Affected**: `MessageListener` and `SlashCommandListener` call `StatusChannelService` after a successful save/mark-complete.
- **Affected**: `ScoreboardService` (or its callers) must expose today's scoreboards for all tracked users to build the table.
- No new dependencies required; Discord4J's existing reactive API is sufficient.
