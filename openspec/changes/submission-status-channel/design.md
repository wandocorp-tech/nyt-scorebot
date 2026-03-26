## Context

NYT Scorebot tracks daily NYT puzzle submissions per-player in a Discord server. Currently, players must scroll through game channels to see who has submitted what. The bot has `MessageListener` and `SlashCommandListener` for ingestion and a `ScoreboardService` for persistence. There is no channel dedicated to surfacing a live submission overview.

The Discord4J reactive client is already wired up. All emoji strings are scattered inline across `MessageListener` and `SlashCommandListener`. The `DiscordChannelProperties` / `application.yml` config describes per-player channels; there is no concept of a status channel yet.

## Goals / Non-Goals

**Goals:**
- Add a configurable status channel where the bot maintains a single, auto-refreshed table of today's submission status for every tracked player.
- Delete any message posted to the status channel by anyone other than the bot.
- Centralise all emoji strings and status indicators in a `BotText` constants class.
- Trigger a status refresh after every successful `saveResult()` call and after `/finished` marks a scoreboard complete.

**Non-Goals:**
- Historical / multi-day status views.
- Per-player opt-in or opt-out of the status channel.
- Pinning the status message (pinning requires extra Discord permissions; delete + repost achieves the same visual result).
- Editing the existing message in-place (edit would preserve the old message ID; delete + repost ensures the message always appears at the bottom of the channel).

## Decisions

### 1. Delete-and-repost vs. in-place edit
**Decision:** Delete the previous bot status message and post a new one.  
**Rationale:** Keeps the status message at the bottom of the channel (most visible), removes need to track and update a pinned message ID across restarts, and avoids complications with Discord's 2000-character edit limit for embeds.  
**Alternative considered:** Edit the existing message. Rejected because the message would remain at its original position in chat history, making it easy to miss.  
**Alternative considered:** Pinned message. Rejected because pinning requires `MANAGE_MESSAGES` per-post and results in intrusive "X pinned a message" system messages.

### 2. Storing the current status message ID
**Decision:** Store the bot's last status message ID in a lightweight in-memory field (`AtomicReference<Snowflake>`) inside `StatusChannelService`, initialised to `null` on startup.  
**Rationale:** The status message is ephemeral (always reflects today); there is no need to survive bot restarts gracefully — on restart the bot simply posts a fresh message. The field must be thread-safe because Discord4J's reactor threads can fire concurrently.  
**Alternative considered:** Persist the message ID to the H2 database. Overkill for a single ephemeral value; adds schema migration complexity.

### 3. Where refresh is triggered
**Decision:** `MessageListener.processMessage()` and `SlashCommandListener` call `StatusChannelService.refresh()` after any successful outcome (`SAVED` for messages, `MARKED_COMPLETE` / `ALREADY_COMPLETE` for `/finished`).  
**Rationale:** Keeps `StatusChannelService` as a passive service; callers decide when a refresh is warranted.  
**Alternative considered:** Observe the database directly. Over-engineered for this use case.

### 4. Table format
**Decision:** Plain text code block (` ```\n...\n``` `) with Unicode box-drawing characters, columns for each game type, and emoji indicators per cell.  
**Rationale:** Discord renders monospace code blocks reliably across all clients (desktop, mobile, web). Rich embeds would be more visually polished but are harder to layout as a grid and count against the 6000-character embed limit.  
**Columns:** Player | Wordle | Connections | Strands | Mini | Midi | Daily | ✅  
**Indicators:** ✅ submitted, ⏳ pending, and the complete flag column shows ✅ / ⬜.

### 5. `BotEmoji` constants class
**Decision:** A single `final class BotEmoji` with `public static final String` fields.  
**Rationale:** Centralises all emoji so changes propagate everywhere. No framework magic needed — plain constants.

### 6. Enforcing read-only access to the status channel
**Decision:** A new `StatusMessageListener` subscribes to `MessageCreateEvent`; if the message was posted in the status channel by anyone other than the bot itself, it is immediately deleted.  
**Rationale:** There is no Discord permission model that allows "bot can post, everyone else cannot" without a channel-level permission override. Reactive deletion is the standard bot approach.

### 7. Configuration
**Decision:** Add a single optional top-level field `statusChannelId` to `DiscordChannelProperties`.  
**Rationale:** Mirrors the existing channel config style. If absent, `StatusChannelService` is a no-op, keeping the feature opt-in.

## Risks / Trade-offs

- **Race condition on concurrent submissions**: Two players submit simultaneously; both trigger a refresh. The delete+repost is not atomic, so a second delete may attempt to delete an already-deleted message. → Mitigation: wrap the delete call in `onErrorResume` to swallow `404 Unknown Message` errors from Discord.
- **Bot restart loses message ID**: On restart, the bot has no record of the previous status message, so the old one remains in the channel until the next refresh causes a new post. → Mitigation: acceptable trade-off given non-goals above; the orphaned message is cosmetic only.
- **Status channel visibility at startup**: The bot does not post a status message on startup, only on the next event. → Mitigation: acceptable; the first submission of the day triggers the first post.

## Migration Plan

1. Add `statusChannelId` to `application.yml` (optional; existing deployments unaffected if omitted).
2. Deploy the updated JAR. No database migration required.
3. Rollback: remove `statusChannelId` from config and redeploy — `StatusChannelService` becomes a no-op.

## Open Questions

- None; all decisions above are resolved.
