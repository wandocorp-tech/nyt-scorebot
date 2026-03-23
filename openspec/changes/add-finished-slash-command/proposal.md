## Why

Scoreboards accumulate results throughout the day, but some users may not submit all puzzle results before the bot needs to process rankings. Without a way to signal completion, the bot has no reliable way to distinguish between a scoreboard that's truly done and one that's still in progress. The `/finished` slash command gives users an explicit way to mark their scoreboard as complete, allowing the bot to safely process and rank partial result sets.

## What Changes

- Add a new Discord slash command `/finished` that sets `complete = true` on the calling user's Scoreboard for the current date
- Register the slash command with Discord so it appears in the command picker
- Wire a slash command interaction listener alongside the existing message listener
- Respond to the user with a confirmation message upon success

## Capabilities

### New Capabilities
- `finished-slash-command`: A `/finished` slash command that marks the current user's daily scoreboard as complete, enabling bot-side processing of incomplete result sets

### Modified Capabilities
- `user-scoreboard-persistence`: The `complete` flag on the Scoreboard entity already exists but is never set to `true` in the current implementation; this change activates it via the slash command

## Impact

- **New code:** Slash command registration logic (on bot startup), interaction event listener, and a service method to mark a scoreboard complete
- **Modified code:** `ScoreboardService` gains a `markComplete` method; `DiscordConfig` or a new component handles slash command registration and interaction events
- **No schema changes:** The `complete` column already exists on the `SCOREBOARD` table
- **Discord permissions:** The bot will require the `applications.commands` scope to register and respond to slash commands
