## Context

The bot currently has no slash commands — all interaction is one-way (message listener → parse → persist). The `Scoreboard` entity already has a `complete` boolean field (default `false`) defined in the `user-scoreboard-persistence` spec, but nothing ever sets it to `true`. Discord4J (the bot's Discord library) supports slash command registration via `ApplicationService` and interaction handling via `ChatInputInteractionEvent` streams.

## Goals / Non-Goals

**Goals:**
- Register a `/finished` global slash command with Discord on bot startup
- Listen for `/finished` interaction events and set `complete = true` on the calling user's Scoreboard for today's date
- Reply to the user in the same interaction to confirm the action
- Handle edge cases: no Scoreboard for today, already marked complete, unknown user

**Non-Goals:**
- Supporting `/finished` for dates other than today
- Undoing a completion (there is no `/unfinished`)
- Admin-level overrides to mark another user's scoreboard complete
- Any scoreboard processing/ranking logic triggered by the completion event

## Decisions

### Decision 1: Slash command registration on startup

**Choice:** Register the `/finished` command as a **global** application command on every bot startup using `ApplicationService#createGlobalApplicationCommand`.

**Rationale:** The bot is a single-guild deployment but global commands are simpler to manage and avoid needing a guild ID in configuration. Discord deduplicates identical command definitions, so re-registering on every startup is idempotent (at most 1-hour propagation delay only on first creation or changes).

**Alternative considered:** Register per-guild using `createGuildApplicationCommand`. This would propagate instantly but requires the guild ID to be configured, adding operational complexity for minimal benefit.

---

### Decision 2: Interaction listener as a separate Spring component

**Choice:** Create a new `SlashCommandListener` Spring `@Component` that subscribes to `ChatInputInteractionEvent` in the same lifecycle pattern as `MessageListener`.

**Rationale:** Keeps concerns separated — `MessageListener` handles message parsing, `SlashCommandListener` handles interactions. Both follow the same reactive subscription pattern already established in the codebase.

**Alternative considered:** Add interaction handling directly into `MessageListener` or `DiscordConfig`. Rejected to avoid bloating existing classes and to maintain single-responsibility.

---

### Decision 3: User/Scoreboard lookup via Discord user ID

**Choice:** Look up the `User` record by matching the Discord user ID from the interaction against the `channelId` field on `User` (which already stores the Discord user ID as configured).

**Rationale:** The existing `UserRepository.findByChannelId` method accepts a `String channelId` that maps to the Discord user ID. This is the established lookup pattern.

**Alternative considered:** Look up by display name. Rejected because display names are not guaranteed unique.

---

### Decision 4: Respond ephemerally

**Choice:** Reply to the `/finished` interaction with an **ephemeral** message (visible only to the invoking user).

**Rationale:** Completion confirmations are personal status updates, not channel announcements. Ephemeral replies reduce noise in shared channels.

## Risks / Trade-offs

- **[Risk] User not found** → If the Discord user who runs `/finished` is not in the bot's configured user list, there is no Scoreboard to mark complete. Mitigation: reply with a clear "you are not a tracked user" message.
- **[Risk] No Scoreboard for today** → The user may not have submitted any results yet. Mitigation: reply with a clear "no scoreboard found for today" message rather than creating an empty completed Scoreboard.
- **[Risk] Already complete** → Running `/finished` twice is harmless (idempotent update), but the user should receive feedback. Mitigation: reply with "already marked complete" message.
- **[Risk] Global command propagation delay** → First-time registration can take up to 1 hour to appear in Discord. This is a Discord limitation, not a code issue.
