## Context

The bot currently parses and persists Wordle, Connections, Strands, and Crossword results from Discord messages, then posts a status table showing submission checkmarks per player. There is no head-to-head comparison view — players must manually compare their emoji grids.

The `designs/scoreboard-rules.md` document specifies the exact layout, spacing, column ordering, winner determination, and result messages for comparison scoreboards. This design describes how to implement those rules in an extensible framework.

The system has two configured players whose channel order defines the "configured order" for tie-breaking column placement. Results are stored as `@Embedded` fields in the `Scoreboard` JPA entity.

## Goals / Non-Goals

**Goals:**
- Implement comparison scoreboard rendering for Wordle, Connections, and Strands per the rules in `scoreboard-rules.md`
- Design a reusable framework so adding a new game scoreboard (e.g., Crossword) requires only implementing a game-specific strategy — no changes to the shared rendering pipeline
- Add `spangram position` tracking to `StrandsResult` for the Strands tiebreaker
- Post rendered scoreboards in the status channel alongside the existing status table
- Achieve ≥80% instruction and branch coverage for all new code

**Non-Goals:**
- Crossword comparison scoreboards (no emoji grid — different format, deferred)
- Historical scoreboard rendering (only today's results)
- Persisting rendered scoreboard text (it is rebuilt on every refresh)
- Changing the existing status table format

## Decisions

### 1. Strategy pattern for per-game scoreboard logic

**Decision:** Define a `GameScoreboardRenderer` interface (or abstract class) with methods for: header text, score label, emoji grid rows, winner determination, and column ordering. Each game type implements this interface. A coordinator class iterates over all renderers to produce the full status channel content.

**Rationale:** This is the classic Strategy pattern and maps naturally to the existing `GameParser` / `@Order` pattern already in the codebase. Adding a new game means adding one new class.

**Alternative considered:** A single monolithic builder with switch statements — rejected because it violates OCP and the user explicitly requested extensibility.

### 2. New `scoreboard` sub-package under `service`

**Decision:** Place all scoreboard rendering classes in `com.wandocorp.nytscorebot.service.scoreboard`:
- `GameComparisonScoreboard` — the interface each game implements
- `ScoreboardRenderer` — the shared layout engine (header, separator, name row, grid, result message)
- `WordleScoreboard`, `ConnectionsScoreboard`, `StrandsScoreboard` — game-specific implementations
- `PlayerColumn` — a value object holding one player's name, score label, and emoji grid rows

**Rationale:** Keeps the `service` package from becoming too large while keeping the scoreboard classes discoverable next to `StatusChannelService` which calls them. The sub-package mirrors how `parser` is already a sibling of `service`.

### 3. Spangram position stored as an explicit field

**Decision:** Add `private Integer spangram_position` to `StrandsResult` (1-based index of the 🟡 emoji in the flattened emoji sequence). Compute it in `StrandsParser` at parse time.

**Rationale:** The scoreboard-rules spec requires the tiebreaker without re-parsing raw content. Storing it at parse time is consistent with how `WordleResult.attempts` and `ConnectionsResult.mistakes` are computed at parse time.

### 4. Emoji grid extraction from rawContent

**Decision:** Each game's scoreboard implementation extracts emoji grid rows from `GameResult.getRawContent()` at render time rather than storing pre-parsed grid rows in the model.

**Rationale:** The raw content is already persisted. Adding grid row fields to the model would require schema migration and duplicate data. Extraction at render time is cheap (simple regex/codepoint scan) and keeps the model clean. This is a rendering concern, not a persistence concern.

### 5. Integration point: Results channel

**Decision:** Scoreboards are posted as separate Discord messages (one per game) in a new **results channel** (configured as `discord.resultsChannelId`, separate from the existing `discord.statusChannelId`). The message ID for each posted scoreboard is tracked in-memory so it can be deleted and reposted if the scoreboard state changes later. The existing status table continues to post to the status channel unchanged.

**Posting lifecycle:**
1. When both players become "done", post a scoreboard message per game to the results channel. Games where one player used `/finished` without submitting will produce single-player scoreboards.
2. If a player subsequently submits a result for a game that already has a posted scoreboard, the existing message is **deleted** and a new completed two-player scoreboard is **posted** in its place (also to the results channel).
3. Games where neither player submitted are skipped (no-op).

**Rationale:** A separate results channel keeps game results distinct from the status table (which is metadata about submission state). Posting each scoreboard as its own message makes individual deletion and replacement straightforward. Tracking message IDs in-memory is sufficient for a single-instance bot. The service owns the Discord posting and deletion concern.

### 6. Column ordering via configured player order

**Decision:** `DiscordChannelProperties.getChannels()` list order defines the "configured order" used in tie scenarios. The first channel is the "left" player in ties.

**Rationale:** The config already has an ordered list. No new configuration needed.

## Risks / Trade-offs

- **Discord message length limit (2000 chars):** If multiple game scoreboards plus the status table exceed 2000 characters, the message will fail. → **Mitigation:** Send scoreboards as separate messages, or split into multiple messages. For the initial two-player setup this is unlikely to be an issue (~600 chars for 3 game boards + status table).

- **Emoji width calibration is empirical:** The spacing constants (leading spaces, gaps) are tuned for Discord's current font rendering. If Discord changes their font or rendering engine, the boards may misalign. → **Mitigation:** Extract spacing constants into the game-specific scoreboard classes so they can be tuned per-game without touching shared code.

- **StrandsResult schema change:** Adding `spangram_position` to an `@Embedded` class requires a new column. → **Mitigation:** H2 `ddl-auto=update` handles this automatically. For production databases, a migration script would be needed, but this project uses auto-DDL.
