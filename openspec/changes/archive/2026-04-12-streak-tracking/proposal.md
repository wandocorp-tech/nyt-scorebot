## Why

The current emoji-based scoreboards (Wordle, Connections, Strands) display a per-day win/tie outcome that only compares two players head-to-head. This provides limited insight and doesn't reward consistency over time. Replacing the win announcement row with per-player streak counts adds a more meaningful, ongoing metric that encourages daily play and makes the scoreboard more engaging.

## What Changes

- **BREAKING**: Remove the win/tie/waiting outcome row from emoji-based scoreboards (Wordle, Connections, Strands). The `determineOutcome()` logic in `WordleScoreboard`, `ConnectionsScoreboard`, and `StrandsScoreboard` is no longer used for display.
- **Add streak persistence**: Introduce a new entity to track current streak count per user per game type. Streaks are stored independently from the daily `Scoreboard` record.
- **Auto-increment on success**: When a player submits a successful game result (Wordle solved, Connections completed, Strands completed), their streak for that game increments by 1.
- **Auto-reset on failure**: When a player submits a failed result (e.g., Wordle X/6, Connections not completed), their streak for that game resets to 0.
- **Add `/streak` slash command**: A new slash command `/streak` accepts a `game` (choice from the three emoji game types) and a `streak` (integer) parameter, allowing users to manually set their current streak for a game.
- **Replace announcement row with streak display**: The bottom row of emoji-based scoreboards now shows each player's current streak for that game instead of the win/tie outcome.
- Crossword scoreboards (Mini, Midi, Main) are **not affected** — they retain their existing win/tie outcome display.

## Capabilities

### New Capabilities
- `streak-tracking`: Core streak persistence, automatic increment/reset on game result submission, `/streak` slash command for manual override, and streak display in emoji-based scoreboard rendering.

### Modified Capabilities
- `crossword-scoreboards`: Crossword scoreboards retain win/tie outcome display but the shared `ScoreboardRenderer` and `GameComparisonScoreboard` interfaces may need refactoring to support two rendering modes (streak vs. outcome). Requirements for crossword outcome display are unchanged.

## Impact

- **Entities**: New `Streak` entity (or embedded fields) in `nyt-scorebot-database` with a repository interface.
- **Service layer**: `ScoreboardService` gains streak update logic on game save. New service method for `/streak` command.
- **Scoreboard rendering**: `ScoreboardRenderer` and the three emoji `*Scoreboard` classes change their bottom row from outcome to streak display. `GameComparisonScoreboard` interface may need adjustment.
- **Discord layer**: `SlashCommandRegistrar` registers `/streak`. `SlashCommandListener` handles it.
- **BotText**: New constants for streak display (e.g., `🔥 3` format), `/streak` command text, and reply messages. Win-related constants (`SCOREBOARD_TIE`, `SCOREBOARD_WIN_WITH_DIFF`, `SCOREBOARD_WIN_NO_DIFF`) may be retained for crossword use only.
- **Database migration**: `spring.jpa.hibernate.ddl-auto=update` will auto-create the new streak table/columns.
- **Tests**: Existing scoreboard comparison tests need updating. New tests for streak increment/reset logic, `/streak` command handling, and streak display rendering.
