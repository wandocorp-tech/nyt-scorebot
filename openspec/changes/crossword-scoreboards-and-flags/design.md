## Context

The NYT Scorebot currently parses and persists crossword results (Mini, Midi, Main) via `CrosswordParser`, storing them as `CrosswordResult` `@Embedded` fields on the `Scoreboard` entity. However, the results channel only renders comparison scoreboards for Wordle, Connections, and Strands — crossword times are invisible there. Additionally, the main crossword has gameplay metadata (duo completion, lookups, checks) that players want to track and compare, but there is no mechanism to record or display it.

The existing `GameComparisonScoreboard` interface and `ScoreboardRenderer` provide a well-established pattern for daily comparison scoreboards. Crossword scoreboards will follow this same pattern, adapted for time-based comparison rather than emoji-grid comparison.

## Goals / Non-Goals

**Goals:**
- Three new daily comparison scoreboards (Mini, Midi, Main) in the results channel showing solve times
- A `MainCrosswordResult` model extracted from `CrosswordResult` with flag fields (`duo`, `lookups`, `checkUsed`)
- Three slash commands (`/duo`, `/lookups`, `/check`) for setting main crossword flags, with toggle behavior and confirmation replies
- Flags displayed as a row beneath the solve time on the main crossword scoreboard

**Non-Goals:**
- Cumulative/weekly/monthly crossword leaderboards (daily comparison only)
- Flags for Mini or Midi crosswords (main crossword only)
- Changing the existing status channel board (it already shows ✅/⏳ for crosswords)
- Crossword streak tracking or historical analysis

## Decisions

### 1. Crossword scoreboards implement `GameComparisonScoreboard`

**Decision:** The three crossword scoreboards implement the existing `GameComparisonScoreboard` interface rather than introducing a new rendering path.

**Rationale:** The interface's contract is general enough — `scoreLabel()` returns the time string (e.g., "12:42"), `emojiGridRows()` returns an empty list for Mini/Midi and a single flags-indicator row for Main. The `ScoreboardRenderer` already handles empty grids gracefully (the grid section is simply omitted).

**Alternative considered:** A separate `TimeComparisonScoreboard` interface and renderer. Rejected because it would duplicate most of the rendering logic (headers, name rows, separators, outcome messages) for minimal gain.

### 2. Extract `MainCrosswordResult` as a subclass of `CrosswordResult`

**Decision:** Create `MainCrosswordResult extends CrosswordResult` as a new `@Embeddable` with three additional fields: `duo` (Boolean, default null/false), `lookups` (Integer, default null), `checkUsed` (Boolean, default null/false). The `Scoreboard` entity's `dailyCrosswordResult` field changes from `CrosswordResult` to `MainCrosswordResult`.

**Rationale:** Keeps Mini/Midi clean of inapplicable fields while preserving the inheritance relationship so existing parser code can still produce a `CrosswordResult` that gets wrapped into a `MainCrosswordResult`. JPA `@Embedded` with `@AttributeOverrides` accommodates the extra columns.

**Alternative considered:** Adding nullable flag fields directly to `CrosswordResult` (simpler, but conceptually muddled — Mini/Midi would carry unused fields). Also considered a separate `MainCrosswordFlags` embeddable alongside the result, but that splits logically related data.

### 3. Crossword outcome determined by solve time (lower wins)

**Decision:** `determineOutcome()` compares `totalSeconds` — lower time wins. The differential is expressed in seconds (e.g., "🏆 Alice wins! (-48)"). If either player's time is null/unparsed, that player loses.

**Rationale:** Consistent with how other games determine outcomes (fewer attempts/mistakes = win). Seconds as the differential integer fits the existing `ComparisonOutcome.Win(String, Integer)` record without modification.

### 4. Slash commands use new outcome enums returned from `ScoreboardService`

**Decision:** Add `SetFlagOutcome` enum: `FLAG_SET`, `FLAG_CLEARED` (for toggles), `NO_MAIN_CROSSWORD`, `NO_SCOREBOARD_FOR_DATE`, `USER_NOT_FOUND`. Service methods return this enum; `SlashCommandListener` maps it to ephemeral reply strings.

**Rationale:** Follows the established `SaveOutcome` / `MarkFinishedOutcome` pattern. Keeps business logic in the service and presentation in the listener.

### 5. Flags row rendering on Main crossword scoreboard

**Decision:** The main crossword scoreboard returns a single-row "emoji grid" containing flag indicators: 👫 for duo, 🔍×N for lookups (only if > 0), ✓ for check used. If no flags are set, the grid row is omitted (empty list returned). Flags are displayed for each player side-by-side using the existing grid alignment logic.

**Rationale:** Reuses the emoji grid rendering path in `ScoreboardRenderer` without any changes to the renderer itself.

### 6. `/lookups` takes an integer argument; `/duo` and `/check` are argumentless toggles

**Decision:** `/duo` and `/check` toggle their respective boolean flag — calling again clears it. The ephemeral reply confirms the new state (e.g., "✅ Duo marked" / "❌ Duo cleared"). `/lookups` accepts a required integer argument and sets the value (calling with 0 clears it). All three require an existing main crossword result for today.

**Rationale:** Toggle behavior is more forgiving (no separate "undo" command needed), and the confirmation reply prevents confusion about current state. `/lookups` is a setter rather than a toggle because it's a numeric value.

### 7. Scoreboard ordering: crosswords come after existing games

**Decision:** Mini (@Order(5)), Midi (@Order(6)), Main (@Order(7)) — after Wordle (1), Connections (2), Strands (3), and the existing fourth slot (4).

**Rationale:** Mirrors the natural play order: word puzzles first, then crosswords by size.

## Risks / Trade-offs

- **`MainCrosswordResult` subclass with `@Embedded`**: JPA handling of embedded inheritance can be finicky. → Mitigation: `MainCrosswordResult` extends `CrosswordResult` and just adds fields; the `@AttributeOverrides` on `Scoreboard` already handle column naming. Verify with H2 schema on startup.
- **Flags row rendering reuses emoji grid path**: The "emoji grid" for crosswords isn't actually emoji — it's text characters (👫, 🔍, ✓). → Mitigation: The renderer's grid alignment uses `codePointCount()` which handles these correctly. The `leadingSpaces`/`baseGap`/`maxEmojisPerRow` config will need tuning for the flag characters.
- **Three new slash commands**: Discord rate limits slash command registration. → Mitigation: Commands are registered once at startup; this is well within Discord's limits.
- **Database migration**: New columns added to the `scoreboard` table. → Mitigation: `ddl-auto=update` handles adding nullable columns. No data migration needed since existing rows will have null flags.
- **Scoreboard width**: The codebase currently uses `BotText.MAX_LINE_WIDTH = 33` (reduced from 35). This reduces horizontal space for labels and flag indicators. → Mitigation: keep column gap unchanged; trim or abbreviate labels if necessary and verify rendering during implementation.
