## ADDED Requirements

### Requirement: Streak entity persists current streak per user per game type
The system SHALL maintain a `Streak` entity that tracks the current consecutive-day streak for each user and game type combination. The entity SHALL contain:
- A reference to the `User` (many-to-one)
- A `gameType` string identifying the game ("Wordle", "Connections", "Strands")
- A `currentStreak` integer (default 0) representing the current consecutive-day count
- A `lastUpdatedDate` (`LocalDate`) recording when the streak was last modified

A unique constraint SHALL enforce that only one streak record exists per (user, gameType) pair.

#### Scenario: First streak record created on initial submission
- **WHEN** a user submits a successful Wordle result and no Streak record exists for (user, "Wordle")
- **THEN** a new Streak record is created with `currentStreak = 1`, `lastUpdatedDate = today`, and `gameType = "Wordle"`

#### Scenario: Streak record exists for user and game
- **WHEN** a user submits a Wordle result and a Streak record already exists for (user, "Wordle")
- **THEN** the existing Streak record is updated (not duplicated)

### Requirement: Streak increments on successful result submission
When a player submits a successful game result on a consecutive day (the day after their last streak update), the system SHALL increment that game's streak by 1 and set `lastUpdatedDate` to today.

Success is defined as:
- **Wordle**: `WordleResult.completed == true`
- **Connections**: `ConnectionsResult.completed == true`
- **Strands**: Always successful (Strands always completes)

#### Scenario: Wordle solved on consecutive day
- **WHEN** a user submits a completed Wordle result (completed = true) and their Wordle streak's `lastUpdatedDate` is yesterday
- **THEN** `currentStreak` increments by 1 and `lastUpdatedDate` is set to today

#### Scenario: Connections completed on consecutive day
- **WHEN** a user submits a completed Connections result (completed = true) and their Connections streak's `lastUpdatedDate` is yesterday
- **THEN** `currentStreak` increments by 1 and `lastUpdatedDate` is set to today

#### Scenario: Strands submitted on consecutive day
- **WHEN** a user submits any Strands result and their Strands streak's `lastUpdatedDate` is yesterday
- **THEN** `currentStreak` increments by 1 and `lastUpdatedDate` is set to today

### Requirement: Streak resets to zero on unsuccessful result
When a player submits a failed game result, the system SHALL reset that game's streak to 0 and set `lastUpdatedDate` to today.

#### Scenario: Wordle failed (X/6)
- **WHEN** a user submits a Wordle result with `completed = false`
- **THEN** the Wordle streak's `currentStreak` is set to 0 and `lastUpdatedDate` is set to today

#### Scenario: Connections failed (not completed)
- **WHEN** a user submits a Connections result with `completed = false`
- **THEN** the Connections streak's `currentStreak` is set to 0 and `lastUpdatedDate` is set to today

### Requirement: Streak resets on missed days (gap detection)
When a player submits a result and their streak's `lastUpdatedDate` is more than one day before today (i.e., they missed at least one day), the system SHALL reset the streak before applying the current result. A successful result after a gap SHALL set the streak to 1. A failed result after a gap SHALL set the streak to 0.

#### Scenario: Successful submission after two-day gap
- **WHEN** a user submits a completed Wordle result and their Wordle streak's `lastUpdatedDate` is 3 days ago
- **THEN** `currentStreak` is set to 1 (not incremented from previous value) and `lastUpdatedDate` is set to today

#### Scenario: Failed submission after a gap
- **WHEN** a user submits a failed Wordle result and their Wordle streak's `lastUpdatedDate` is 2 days ago
- **THEN** `currentStreak` is set to 0 and `lastUpdatedDate` is set to today

#### Scenario: First-ever submission (no prior streak record)
- **WHEN** a user submits a successful result and no Streak record exists for that game type
- **THEN** a Streak record is created with `currentStreak = 1` and `lastUpdatedDate = today`

### Requirement: /streak slash command for manual streak override
The system SHALL register a `/streak` global slash command with two required parameters:
- `game` — a string choice restricted to: "Wordle", "Connections", "Strands"
- `streak` — an integer with minimum value 0

When invoked, the command SHALL:
1. Identify the invoking user by their Discord user ID
2. Find or create the Streak record for (user, game)
3. Set `currentStreak` to the provided value
4. Set `lastUpdatedDate` to today
5. Reply with an ephemeral confirmation message

If the invoking user is not a tracked user in the bot, the command SHALL reply with an ephemeral error message.

#### Scenario: Set Wordle streak to 5
- **WHEN** a tracked user invokes `/streak game:Wordle streak:5`
- **THEN** their Wordle streak is set to `currentStreak = 5`, `lastUpdatedDate = today`, and an ephemeral reply confirms: "✅ Wordle streak set to 5"

#### Scenario: Reset Connections streak to 0
- **WHEN** a tracked user invokes `/streak game:Connections streak:0`
- **THEN** their Connections streak is set to `currentStreak = 0`, `lastUpdatedDate = today`, and an ephemeral reply confirms: "✅ Connections streak set to 0"

#### Scenario: Non-tracked user invokes /streak
- **WHEN** an unrecognized Discord user invokes `/streak game:Wordle streak:3`
- **THEN** the command replies with an ephemeral error: "You are not a tracked user in this bot."

#### Scenario: Negative streak value
- **WHEN** a user invokes `/streak game:Wordle streak:-1`
- **THEN** the command replies with an ephemeral error: "⚠️ Value must be non-negative."

### Requirement: Emoji-based scoreboards display streak row instead of outcome row
For emoji-based game scoreboards (Wordle, Connections, Strands), the bottom row of the two-player comparison SHALL display each player's current streak for that game instead of the win/tie/waiting outcome message. The streak row SHALL align each player's streak with their emoji grid column, using the format:

```
<streak1>        <streak2>
```

Player names are not repeated in the streak row — they are already present in the header row. Crossword scoreboards (Mini, Midi, Main) SHALL continue to display the existing win/tie outcome row.

#### Scenario: Both players submitted Wordle with active streaks
- **WHEN** both players have submitted Wordle results today, player A has a Wordle streak of 5, and player B has a Wordle streak of 3
- **THEN** the Wordle scoreboard's bottom row displays `5` under player A's column and `3` under player B's column, aligned with their emoji grids

#### Scenario: Only one player submitted with streaks displayed
- **WHEN** only player A has submitted a Wordle result and has a streak of 5, and player B has a stored streak of 3 (from yesterday)
- **THEN** the Wordle scoreboard renders player A's grid on one side and the bottom row displays `5` and `3` in each player's respective column, using stored streak values

#### Scenario: Both players have zero streaks
- **WHEN** both players have a Wordle streak of 0
- **THEN** the scoreboard bottom row displays `0` in each player's column

#### Scenario: Crossword scoreboard retains outcome display
- **WHEN** both players have submitted Mini crossword results
- **THEN** the Mini crossword scoreboard displays the win/tie outcome row (e.g., "🏆 A wins! (-48)") — not a streak row

### Requirement: GameComparisonScoreboard interface supports streak vs. outcome distinction
The `GameComparisonScoreboard` interface SHALL include a `boolean usesStreakDisplay()` method. Implementations for emoji-based games (Wordle, Connections, Strands) SHALL return `true`. Implementations for crossword games (Mini, Midi, Main) SHALL return `false`.

The `ScoreboardRenderer` SHALL use this method to determine whether to render a streak row or an outcome row for each game type.

#### Scenario: WordleScoreboard returns usesStreakDisplay true
- **WHEN** `WordleScoreboard.usesStreakDisplay()` is called
- **THEN** it returns `true`

#### Scenario: MiniCrosswordScoreboard returns usesStreakDisplay false
- **WHEN** `MiniCrosswordScoreboard.usesStreakDisplay()` is called
- **THEN** it returns `false`

### Requirement: Streak update is atomic with result persistence
The streak update SHALL occur within the same transaction as the game result save in `ScoreboardService.saveResult()`. If the result save fails or is rejected (wrong puzzle number, duplicate), the streak SHALL NOT be modified.

#### Scenario: Streak not updated on duplicate submission
- **WHEN** a user submits a Wordle result that is rejected as `ALREADY_SUBMITTED`
- **THEN** the Wordle streak remains unchanged

#### Scenario: Streak not updated on wrong puzzle number
- **WHEN** a user submits a Wordle result that is rejected as `WRONG_PUZZLE_NUMBER`
- **THEN** the Wordle streak remains unchanged

#### Scenario: Streak updated atomically with successful save
- **WHEN** a user submits a valid, successful Wordle result that is saved
- **THEN** the streak is updated in the same database transaction as the scoreboard save

### Requirement: Date calculations use PuzzleCalendar
All date comparisons for streak logic (determining "today", checking if `lastUpdatedDate` was "yesterday") SHALL use `PuzzleCalendar.today()` to ensure consistency with puzzle number validation and timezone handling (GMT/BST).

#### Scenario: Streak gap detection uses puzzle calendar date
- **WHEN** a user submits a result and the system checks for day gaps
- **THEN** "today" and "yesterday" are determined by `PuzzleCalendar.today()`, not `LocalDate.now()`
