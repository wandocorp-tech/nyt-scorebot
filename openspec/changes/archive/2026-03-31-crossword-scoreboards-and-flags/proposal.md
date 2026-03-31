## Why

The bot currently tracks crossword solve times but only shows a ✅/⏳ status indicator — solve times are invisible in the results channel. Other games (Wordle, Connections, Strands) have rich daily comparison scoreboards with win/tie/loss outcomes. Crossword results deserve the same treatment, plus the main crossword has additional metadata (duo completion, lookups used, checks used) that players want to record and compare.

## What Changes

- **Three new daily comparison scoreboards** for Mini, Midi, and Main crosswords, displayed in the existing results channel alongside the other game scoreboards. Each shows actual solve times with a win/tie/unsubmitted outcome row following the same pattern as existing game scoreboards.
- **Extract `MainCrosswordResult` model** from the current `CrosswordResult` to hold main-crossword-specific flags: `duo` (boolean), `lookups` (integer), and `checkUsed` (boolean).
- **Three new slash commands** for recording main crossword flags:
  - `/duo` — toggles the duo completion flag on today's main crossword result. Ephemeral reply confirms current state.
  - `/lookups {count}` — records the number of lookups used. Accepts an integer argument.
  - `/check` — toggles the check-used flag on today's main crossword result. Ephemeral reply confirms current state.
- **Flags displayed on the main crossword scoreboard** as a row beneath the solve time.
- All three slash commands **require a main crossword result to already be submitted** for today before the flag can be set.

## Capabilities

### New Capabilities
- `crossword-scoreboards`: Three daily comparison scoreboards (Mini, Midi, Main) showing solve times side-by-side with win/tie/unsubmitted outcomes, rendered in the results channel.
- `main-crossword-flags`: Slash commands (`/duo`, `/lookups`, `/check`) for recording and toggling main crossword metadata, with the `MainCrosswordResult` model to persist them.

### Modified Capabilities
- `user-scoreboard-persistence`: The `Scoreboard` entity's `dailyCrosswordResult` field changes from `CrosswordResult` to the new `MainCrosswordResult` type, which extends `CrosswordResult` with flag fields.

## Impact

- **Model layer**: New `MainCrosswordResult` embeddable extracted from `CrosswordResult`; `Scoreboard` entity field type changes for the main crossword.
- **Service layer**: `ScoreboardService` gains flag-setting methods with new outcome enums; three new `GameComparisonScoreboard` implementations for crossword rendering.
- **Listener layer**: `SlashCommandListener` registers three new commands with Discord.
- **Database**: New columns for `duo`, `lookups`, `check_used` on the scoreboard table (via JPA `@Embedded` attribute overrides). H2 `ddl-auto=update` handles migration.
- **Discord config**: Three new slash commands registered with the Discord API on bot startup, and an optional `discord.resultsChannelId` configuration property to enable the dedicated results channel.
- **BotText / Rendering**: The application uses `BotText.MAX_LINE_WIDTH` (currently 33) to set scoreboard width. Implementations MUST ensure rendered lines do not exceed this width. New UI strings for crossword scoreboard labels, flag indicators, and slash command replies.
