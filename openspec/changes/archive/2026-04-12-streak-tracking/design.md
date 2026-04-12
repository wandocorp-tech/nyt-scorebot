## Context

The NYT Scorebot currently renders emoji-based scoreboards (Wordle, Connections, Strands) with a win/tie/waiting announcement row produced by `GameComparisonScoreboard.determineOutcome()`. Crossword scoreboards (Mini, Midi, Main) use the same interface and also display a win/tie outcome. The `ScoreboardRenderer.renderTwoPlayer()` method calls `buildResultMessage()` to format the bottom row for all game types uniformly.

There is no concept of streaks today — each day's results are self-contained in a `Scoreboard` entity (one row per user per day) with no cross-day aggregation.

The two tracked players are configured via `DiscordChannelProperties.channels` with fixed names and channel IDs.

## Goals / Non-Goals

**Goals:**
- Introduce per-user, per-game streak tracking that persists across days
- Automatically increment streaks on successful daily results and reset on failure or missed days
- Provide a `/streak` slash command for manual streak override (for seeding or corrections)
- Replace the announcement row in emoji-based scoreboards (Wordle, Connections, Strands) with a streak display row
- Retain existing win/tie outcome display for crossword scoreboards (Mini, Midi, Main)

**Non-Goals:**
- Historical streak calculation from existing `Scoreboard` data (streaks start from 0 or manual seed)
- "Best streak" / all-time streak tracking (only current streak is tracked)
- Streak tracking for crossword game types
- Changing the status channel table layout (streaks only appear in the results channel scoreboards)
- Streak notifications or announcements beyond the scoreboard display

## Decisions

### 1. New `Streak` entity in `nyt-scorebot-database`

**Decision:** Create a new `Streak` JPA entity rather than adding fields to `User` or `Scoreboard`.

**Rationale:** A dedicated entity cleanly models the (user, gameType) → streak relationship. Adding fields to `User` would require 3+ new columns and break the single-responsibility of that entity. Adding to `Scoreboard` conflates per-day results with cross-day aggregation.

**Schema:**
```
Streak
├── id: Long (PK, auto-generated)
├── user: User (FK, many-to-one)
├── gameType: String ("Wordle", "Connections", "Strands")
├── currentStreak: int (default 0)
└── lastUpdatedDate: LocalDate
    unique constraint on (user_id, game_type)
```

**Alternatives considered:**
- Embed streak fields on `User` — simpler but mixes concerns, hard to extend to new game types
- Compute streaks from historical `Scoreboard` data — expensive query, fragile if data has gaps

### 2. Lazy gap detection for missed days

**Decision:** When processing a new game result, check the `Streak.lastUpdatedDate` to detect missed days. If the gap between `lastUpdatedDate` and today is > 1 day, reset the streak to 0 before applying the current result.

**Rationale:** This avoids needing a scheduled job or daily cron to reset streaks. The streak value is only meaningful when a player submits, so lazy evaluation at submission time is sufficient. The streak display always reflects the correct value because it reads `currentStreak` after the lazy reset.

**Gap detection logic:**
1. `lastUpdatedDate == today` → result already processed today (should not happen due to duplicate check, but no-op if it does)
2. `lastUpdatedDate == yesterday` → consecutive day; apply success/failure normally
3. `lastUpdatedDate < yesterday` (or null) → gap detected; reset streak to 0 before applying result

**After gap detection, apply result:**
- Success → `currentStreak++`, `lastUpdatedDate = today`
- Failure → `currentStreak = 0`, `lastUpdatedDate = today`

**Alternatives considered:**
- Scheduled daily job to reset all unfilled streaks — adds operational complexity, could miss edge cases around timezone boundaries
- Check at display time instead of save time — would require the renderer to have DB access and timezone awareness

### 3. Success criteria per game type

**Decision:** Define success as:
- **Wordle:** `WordleResult.getCompleted() == true` (puzzle solved in ≤ 6 attempts)
- **Connections:** `ConnectionsResult.getCompleted() == true` (all groups found within mistake limit)
- **Strands:** Always successful (Strands always completes — the game provides hints until you finish)

**Rationale:** Matches the existing `determineOutcome()` semantics. Strands has no failure mode per the current parser — `StrandsScoreboard.determineOutcome()` never checks for completion, only compares hint counts.

### 4. Differentiate emoji vs. crossword scoreboards in rendering

**Decision:** Add a `boolean usesStreakDisplay()` method to `GameComparisonScoreboard`. Emoji scoreboards (Wordle, Connections, Strands) return `true`; crossword scoreboards return `false`.

In `ScoreboardRenderer.renderTwoPlayer()`:
- If `game.usesStreakDisplay()` → render a streak row (e.g., `🔥 5        🔥 3`, aligned with emoji grid columns)
- Else → render the existing outcome row via `buildResultMessage()`

**Rationale:** A method on the interface is the simplest, most extensible approach. No need for marker interfaces, enum-based dispatch, or instanceof checks. New game types added in the future simply implement the method.

**Alternatives considered:**
- Separate interfaces for streak-based vs. outcome-based scoreboards — over-engineering for a boolean distinction
- Check `emojiGridRows().isEmpty()` as a proxy — fragile and semantically wrong

### 5. Streak display in the scoreboard renderer

**Decision:** The streak row replaces the announcement row at the bottom of emoji scoreboards. Each player's streak is aligned with their emoji grid column:

```
🔥 5        🔥 3
```

Player names are not included in the streak row — they already appear in the header row. When one or both players haven't submitted, the streak row still shows current stored streaks (which may be stale until that player submits). The `⏳ hasn't submitted` message moves from the result row to a status indicator in the name/score row (no result posted yet means no score label — display `?` instead).

**Rationale:** Streaks are always displayable because they are stored state. This means the scoreboard can always render a streak row even when only one player has submitted. The "waiting" concept shifts from the result row to the score label. Repeating names in the streak row is redundant since they appear in the header row above the emoji grids.

### 6. `/streak` slash command design

**Decision:** Register a new `/streak` global slash command with two required parameters:
- `game` — String choice from: Wordle, Connections, Strands
- `streak` — Integer, minimum 0

The command looks up the invoking user via Discord user ID, finds or creates the `Streak` entity for (user, gameType), and sets `currentStreak` to the given value and `lastUpdatedDate` to today.

Returns an ephemeral reply confirming the update: `✅ Wordle streak set to 5`

**Rationale:** Manual override is needed for initial setup (players have existing streaks not tracked by the bot) and corrections. Setting `lastUpdatedDate = today` prevents the lazy reset from immediately clearing a manually set streak.

### 7. Streak update integration point

**Decision:** Streak updates happen inside `ScoreboardService.saveResult()`, after the game result is successfully persisted (i.e., `SaveOutcome.SAVED`). A new `StreakService` encapsulates the streak logic and is called from `ScoreboardService`.

**Rationale:** `ScoreboardService` is the transaction boundary for result persistence. Updating the streak in the same transaction ensures atomicity. A separate `StreakService` keeps streak logic isolated and testable.

### 8. `StreakService` in `nyt-scorebot-service` module

**Decision:** Create `StreakService` in the service module with methods:
- `updateStreak(User user, GameResult result)` — performs lazy gap detection + success/failure logic
- `setStreak(User user, String gameType, int value)` — for the `/streak` command
- `getStreaks(User user)` — returns all streaks for a user (used by renderer)
- `getStreak(User user, String gameType)` — returns a single streak value

The renderer receives streak data as a `Map<String, Map<String, Integer>>` (playerName → gameType → streak) passed from `ResultsChannelService`, which fetches it before rendering.

## Risks / Trade-offs

- **[Stale streak on display before submission]** → When only one player has submitted, the other player's streak is shown from storage (possibly from yesterday). This is acceptable — it's the "current" streak and will update when they submit. Mitigated by showing `?` as the score label for unsubmitted players.

- **[Strands streak always increments]** → Since Strands has no failure case, the streak is effectively a "days played" counter. This is intentional per the game's design but may feel less meaningful. → Users can manually reset via `/streak Strands 0` if desired.

- **[No historical backfill]** → Existing players start with streak 0. → Mitigated by the `/streak` command for manual seeding.

- **[Lazy reset depends on submission timezone]** → If a player submits after midnight in one timezone but before midnight in the puzzle's timezone, the gap detection could misfire. → Mitigated by using `PuzzleCalendar.today()` consistently (GMT/BST-based), same as puzzle number validation.

- **[Database migration]** → New `streak` table auto-created by `ddl-auto=update`. → Low risk for H2, but worth noting. No data migration needed for existing tables.
