## Why

When both players submit results for the same game (Wordle, Connections, Strands), there is no visual head-to-head comparison. Players must manually compare their emoji grids and guess counts. A formatted comparison scoreboard for each game type, posted to the status channel, provides an at-a-glance view of who won, by how much, and how the two grids compare row-by-row.

## What Changes

- Add a **game comparison scoreboard renderer** for each emoji-grid game: Wordle, Connections, and Strands.
- Each renderer produces a fixed-width (35-character) Discord code-block scoreboard with header, player names/scores, side-by-side emoji grids, and a result message.
- Support three layouts: **two-player** (winner/loser or tie), **single-player** (one result submitted, waiting for the other), and **no-op** (neither submitted — no board posted).
- Implement **winner determination** logic per game: Wordle (fewer guesses), Connections (fewer mistakes), Strands (fewer hints, then earlier spangram tiebreaker).
- Add a `spangram position` field to `StrandsResult` (1-based index in the emoji sequence) for the Strands tiebreaker.
- Integrate scoreboard rendering into a new **results channel** (configured separately from the existing status channel) so comparison boards are posted once both players have either submitted all games for the day or marked themselves as finished via the `/finished` command. The status table continues to be posted to the existing status channel.
- A player who marks themselves finished without submitting all games will trigger scoreboards to be posted with single-player layouts for any unsubmitted games.
- If a single-player scoreboard has already been posted and the second player subsequently submits a result, the incomplete scoreboard SHALL be deleted and replaced with the completed two-player scoreboard.
- All scoreboard components must be **extensible** — new game scoreboards (e.g., Crossword) can be added later without modifying the shared framework.
- Maintain **≥80% code coverage** with unit tests for all new components.

## Capabilities

### New Capabilities
- `game-scoreboard-rendering`: Framework for rendering fixed-width comparison scoreboards (shared layout, column ordering, result messages) with per-game specialisation (header, score field, emoji grid, winner logic).
- `strands-spangram-tracking`: Store the spangram position (1-based index) in `StrandsResult` so the tiebreaker can be evaluated at scoreboard-build time without re-parsing raw content.

### Modified Capabilities

_(none — no existing spec-level requirements are changing)_

## Impact

- **Model**: `StrandsResult` gains a `spangram_position` column; `StrandsParser` updated to compute it.
- **Config**: Add `discord.resultsChannelId` to `DiscordChannelProperties` for the new results channel (separate from `discord.statusChannelId`).
- **Service**: New scoreboard-builder classes in `service/` (or a new `scoreboard/` sub-package). `StatusChannelService` posts status table to status channel and results to the new results channel. A new `ResultsChannelService` may be created to isolate results posting concerns.
- **BotText**: New constants for scoreboard result messages (tie, win, waiting).
- **Entity**: `Scoreboard` entity needs an `@AttributeOverride` for the new `StrandsResult.spangram_position` column.
- **Tests**: New unit tests for each game scoreboard builder, winner determination, and layout formatting. Parser test updated for spangram position.
- **Database**: H2 auto-DDL will add the new column on next startup (`ddl-auto=update`).
