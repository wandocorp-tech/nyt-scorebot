## Context

Two services in `nyt-scorebot-discord` manage long-lived channel messages:

1. **`ResultsChannelService`** — posts six per-day scoreboards (Wordle, Connections, Strands, Mini, Midi, Main) plus a win-streak summary. Tracks message IDs in a `Map<String, Snowflake> postedMessageIds`. Today, `refresh()` and `refreshGame(...)` use `deleteAndRepost(...)` for any slot that already has a message ID.
2. **`StatusChannelService`** — posts a single live status table summarising who has submitted what so far today. Tracks the message ID in `AtomicReference<Snowflake> lastMessageId`. Today, `refresh(...)` always deletes the previous message and posts a new one (which bubbles it to the bottom of the channel).

The recently-added win-streak summary already edits in place via `Message.edit(spec -> spec.setContent(...))`. This change extends that pattern to the other slots and centralises it.

Discord4J's `Message.edit(Consumer<LegacyMessageEditSpec>)` returns `Mono<Message>` and propagates an error if the message no longer exists (e.g. it was deleted manually or by another bot). Today's delete-then-repost is silently tolerant of this via `.onErrorResume(e -> Mono.empty())` on the delete leg.

## Goals / Non-Goals

**Goals:**
- Replace delete-then-repost with edit-in-place for all long-lived message slots (results scoreboards, win-streak summary, status board).
- Provide a single shared helper so the edit-or-post-with-fallback logic is not duplicated across call sites.
- Preserve resilience: if an edit fails because the underlying message is gone, fall back to posting a fresh message and update the tracked ID.
- Keep the current notification behaviour for the *initial* post of the day (so the channel still gets a ping when scoreboards first appear).
- Split the status board into two concerns: a persistent table message that is edited in place and reset at midnight, and a separate short-lived notification message that fires the Discord push and is auto-deleted.

**Non-Goals:**
- Changing what is rendered inside any message body (other than removing the context-message header from the status board).
- Changing when refreshes are triggered.
- Adding any persistence of message IDs across bot restarts (today they live in memory; that remains true).
- Reworking the win-streak summary path — it already edits in place; it will simply be migrated to use the new shared helper.
- Rewriting `WinStreakMidnightJob` — the new `StatusBoardMidnightJob` is a sibling, not a replacement.

## Decisions

### Decision 1: Edit always, fall back to post on failure
**Choice:** When a tracked message ID exists for a slot, attempt `Message.edit(...)`. If the edit `Mono` errors (e.g. `404 Not Found` because the message was deleted), fall back to `createMessage(...)` and update the tracked ID.

**Alternatives considered:**
- *Probe-then-edit-or-post* (call `getMessageById` first, branch on presence). Rejected — adds a round-trip and a TOCTOU race; the edit attempt itself is an authoritative existence check.
- *Optimistic edit with no fallback*. Rejected — would silently stop updating that slot for the rest of the day if the message was deleted manually.

### Decision 2: Centralise the helper in a small reusable component
**Choice:** Extract a private method (or a small package-private collaborator class, e.g. `MessageSlotWriter`) shared by `ResultsChannelService` and `StatusChannelService`. Signature roughly:

```java
Mono<Snowflake> editOrPost(Snowflake channelId, @Nullable Snowflake existingId, String content)
```

The caller is responsible for storing the returned `Snowflake` in its own slot map / atomic ref. Centralising keeps the fallback semantics consistent.

**Alternatives considered:**
- *Duplicate the logic in each service*. Rejected — three call sites is over the threshold where copy-paste bugs creep in (the win-streak summary path already drifted slightly from the scoreboard path).

### Decision 3: Fire-and-forget subscribe at the call site
**Choice:** Keep the existing `.subscribe(v -> {}, error -> log.error(...))` pattern at the service-level call sites. The shared helper returns a `Mono` and does not subscribe.

**Rationale:** Matches the existing reactive style in both services; avoids the helper making policy decisions about logging or back-pressure.

### Decision 4: Status board is one persistent message, reset at midnight
**Choice:** The status board lives as a single persistent message that the bot creates the first time it refreshes the status channel and reuses for every update thereafter. Its position in the channel is fixed (no bubbling). A new `StatusBoardMidnightJob` (`@Scheduled(cron = "0 0 0 * * *", zone = "${discord.timezone:Europe/London}")`) edits this same message back to its empty/base state (all `⏳`, no one finished) for the new day.

**Rationale:** Treating the status board as a long-lived UI element (not a stream of messages) matches how users actually consume it — they glance at it, they don't scroll a history of it. A persistent message also gives the midnight job a stable target to edit.

**Trade-off acknowledged:** The status board no longer bubbles to the latest position on every update. The new ephemeral-notification message (Decision 5) restores the "something happened" cue.

**Alternatives considered:**
- *Post a brand-new status board each midnight*. Rejected — leaves yesterday's message in the channel as clutter, and means the tracked ID must be replaced atomically.
- *No midnight reset; let the next submission of the day implicitly clear stale state*. Rejected — leaves stale ✅/❌ visible from midnight until the first submission, which can be hours.

### Decision 5: Context message becomes an ephemeral notification post
**Choice:** The `contextMessage` (e.g. "Conor submitted Wordle", "Will marked themselves finished", "Conor flagged Main as duo") is posted as its own message in the status channel and auto-deleted after a fixed delay of **10 seconds**. The persistent status board is edited in place separately.

**Rationale:** Splits the two concerns the status channel currently conflates: notifying users that something happened (ephemeral, push-noise) and showing the current state (persistent, glanceable).

**Why 10 seconds:** Discord notifications are delivered on post and persist on most clients even after deletion, but very fast deletes (sub-second) can race the notification fan-out. 10 seconds is comfortably above the fan-out window, gives in-channel viewers time to read the line in real time, and is short enough to avoid clutter. If a different default proves better, the delay can be made configurable via a `discord.statusNotificationTtlSeconds` property in a follow-up.

**Implementation:** Use Discord4J's reactive scheduling — `Mono.delay(Duration.ofSeconds(10))` chained to `Message::delete`, subscribed fire-and-forget. No persistence is needed: if the bot restarts within the 10-second window, the message simply remains.

**Alternatives considered:**
- *Immediate post-and-delete*. Rejected — risks dropped notifications on some clients; offers no user-visible benefit over a brief window.
- *Use Discord's "ephemeral" interaction reply*. Rejected — only applies to slash-command interactions, not to the bot-driven refreshes triggered by message-content parsing.
- *Push the notification to a DM channel instead*. Rejected — the existing UX puts these signals in the status channel; users have already opted in via channel notifications.

### Decision 6: `StatusMessageBuilder` drops the `contextMessage` parameter
**Choice:** Remove `contextMessage` from `StatusMessageBuilder`'s constructor and from the rendered output. The builder produces only the table.

**Rationale:** Keeps the builder a pure renderer of state, with no event-log responsibility. The ephemeral notification is plain text, built inline by `StatusChannelService` from the same `BotText.STATUS_CONTEXT_*` constants the callers already use.

### Decision 7: Centralise the helper in a small reusable component
**Choice:** Extract a private method (or a small package-private collaborator class, e.g. `MessageSlotWriter`) shared by `ResultsChannelService` and `StatusChannelService`. Signature roughly:

```java
Mono<Snowflake> editOrPost(Snowflake channelId, @Nullable Snowflake existingId, String content)
```

The caller is responsible for storing the returned `Snowflake` in its own slot map / atomic ref. Centralising keeps the fallback semantics consistent.

**Alternatives considered:**
- *Duplicate the logic in each service*. Rejected — three call sites is over the threshold where copy-paste bugs creep in (the win-streak summary path already drifted slightly from the scoreboard path).

### Decision 8: Fire-and-forget subscribe at the call site
**Choice:** Keep the existing `.subscribe(v -> {}, error -> log.error(...))` pattern at the service-level call sites. The shared helper returns a `Mono` and does not subscribe. The ephemeral-notification post-then-delete chain follows the same pattern.

**Rationale:** Matches the existing reactive style in both services; avoids the helper making policy decisions about logging or back-pressure.

### Decision 9: First-post behaviour is unchanged
The very first post of the day for any slot (no tracked ID yet) still uses `createMessage(...)` and produces a normal Discord notification. Only subsequent updates within the same lifetime of the message ID are edited in place.

## Risks / Trade-offs

- **[Risk]** `Message.edit` may fail with a transient 5xx → **Mitigation:** Treat any error as the "message gone" case and fall back to posting. Worst case is one duplicated message per transient failure, which matches today's risk envelope.
- **[Risk]** Discord rate limits on `Message.edit` are separate from `createMessage` limits, but Discord4J handles per-route rate limiting transparently. → **Mitigation:** No special handling needed; rely on Discord4J's built-in router.
- **[Risk]** Ephemeral notification not delivered before delete on slow clients → **Mitigation:** 10-second delay is well above Discord's notification fan-out window. Risk is theoretical only.
- **[Risk]** Bot restarts between status-board edits, losing the tracked message ID → **Mitigation:** Next refresh posts a fresh status board; yesterday's untracked message is left as-is. Acceptable; matches today's behaviour.
- **[Risk]** Midnight job edits the status board while a refresh is in flight → **Mitigation:** Both paths use the same in-memory `lastMessageId` reference; the last write wins. Cron fires at exactly 00:00 when no submission traffic is expected, so the race is theoretical. If needed, a follow-up could synchronise on the `AtomicReference` or use a `Mono.fromCallable` pipeline.
- **[Risk]** A test that asserted on `Message::delete` being called will need to be updated. → **Mitigation:** Covered in the tasks list.
- **[Trade-off]** Edits do not produce notifications, which is the *point* — the ephemeral notification message restores the ping in a deliberate, scoped way.

## Migration Plan

No data migration. Behavioural change deploys with the next release; no feature flag is required because the fallback path preserves the current "always-eventually-correct" guarantee.

On first deploy, the existing status-board message (if any) becomes an untracked legacy message; the bot will post a new one on its next refresh. Operators may delete the legacy message manually.

Rollback: revert the commit. There is no persistent state introduced or changed.
