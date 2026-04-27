## Why

The current Main crossword scoreboard determines the winner purely by `totalSeconds`, ignoring the `checkUsed` and `lookups` flags. This rewards players who used in-puzzle assistance over those who solved unaided, which contradicts the intended house rules. Additionally, all time-equal ties (Mini, Midi, Main) currently render the same generic "🤝 Tie!" message — players want a special "Nuke!" callout when two clean solves land on exactly the same time.

## What Changes

- Replace the time-only Main crossword win logic with rules that account for the `checkUsed` and `lookups` flags:
  - If neither player used a check or any lookups, the lower time wins (current behaviour).
  - If exactly one player used a check or one-or-more lookups, the **other** player wins regardless of time.
  - If both players used a check or lookups, the result is a draw regardless of time.
- Add a new "Nuke!" outcome that fires when both players post identical times **without** any qualifying assistance flags. This applies to:
  - Main crossword (only when neither player used check/lookups).
  - Mini crossword (any equal-time tie).
  - Midi crossword (any equal-time tie).
- Existing generic tie message ("🤝 Tie!") is retained for the Main crossword "both used assistance" draw case.
- The `duo` flag remains informational and does NOT factor into win determination.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `crossword-scoreboards`: Main crossword win/tie determination now incorporates the `checkUsed` and `lookups` flags; all three crossword scoreboards (Mini, Midi, Main) gain a "Nuke!" outcome for time-equal clean solves.

## Impact

- **Code**:
  - `nyt-scorebot-service/.../scoreboard/MainCrosswordScoreboard.java` — rewrite `determineOutcome`.
  - `nyt-scorebot-service/.../scoreboard/MiniCrosswordScoreboard.java` and `MidiCrosswordScoreboard.java` — return new `Nuke` outcome on equal times.
  - `nyt-scorebot-service/.../scoreboard/ComparisonOutcome.java` — add `Nuke` variant to the sealed interface.
  - `nyt-scorebot-service/.../scoreboard/ScoreboardRenderer.java` — handle `Nuke` outcome in `buildResultMessage`.
  - `nyt-scorebot-domain/.../BotText.java` — add `SCOREBOARD_NUKE` constant ("☢️ Nuke!").
- **Tests**: Update unit tests for the three crossword scoreboard classes and `ScoreboardRenderer`. Add coverage for the new flag-based decision branches and the Nuke outcome.
- **Specs**: Delta on `crossword-scoreboards` capability.
- **No DB / API / dependency changes.**
