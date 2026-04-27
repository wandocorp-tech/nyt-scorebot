## Context

`MainCrosswordScoreboard.determineOutcome` currently compares `totalSeconds` only and ignores the `checkUsed` and `lookups` flags carried on `MainCrosswordResult`. House rules treat any usage of an in-puzzle check or lookup as a fairness penalty, so the unaided solver should always win head-to-head against an aided one. Mini and Midi crosswords have no flags, so their logic is unchanged for the time-based win path — but the team also wants a more dramatic outcome line when two equally-clean times collide on any of the three crosswords.

`ComparisonOutcome` is a sealed interface with three variants (`Tie`, `Win`, `WaitingFor`); `ScoreboardRenderer.buildResultMessage` does an `instanceof` chain over them. Adding a new variant is a contained change.

## Goals / Non-Goals

**Goals:**
- Make Main crossword win/draw determination respect the `checkUsed` and `lookups` flags per the rules in the proposal.
- Introduce a single new "Nuke!" outcome shared by all three crossword scoreboards for clean-tie-on-time scenarios.
- Keep the existing generic "🤝 Tie!" outcome for Main's both-aided draw case.

**Non-Goals:**
- Changing the `duo` flag's role (it stays informational and out of the win calculation).
- Reworking how flags are persisted, parsed, or rendered in the flags row.
- Touching Wordle, Connections, or Strands outcome logic or display format.
- Adding any new flag types or `/finished` semantics.

## Decisions

### Decision 1: Add `Nuke` as a new sealed variant of `ComparisonOutcome`
**Choice:** Extend `ComparisonOutcome` with `record Nuke() implements ComparisonOutcome {}` and update the sealed `permits` clause.

**Rationale:** A distinct variant keeps the rendering decision in one place (`ScoreboardRenderer.buildResultMessage`) and lets each `GameComparisonScoreboard` implementation decide whether a tie is a Nuke or an ordinary tie. Reusing `Tie` and threading a boolean would leak rendering concerns into call sites.

**Alternatives considered:**
- Add a `boolean nuke` field to `Tie` — rejected because it muddies the variant's meaning and forces every consumer to inspect the field.
- Render the message inside each scoreboard impl — rejected because it duplicates `BotText` lookups and breaks the current single-source rendering pattern.

### Decision 2: "Used assistance" predicate
**Choice:** A `MainCrosswordResult` is considered to have used assistance when `Boolean.TRUE.equals(checkUsed)` OR (`lookups != null && lookups > 0`). `duo` is excluded.

**Rationale:** Matches the user's stated rule. Null-safe handling mirrors `MainCrosswordScoreboard.buildFlagsString`.

### Decision 3: Decision tree for `MainCrosswordScoreboard.determineOutcome`
```
aided1 = usedAssistance(s1.main)
aided2 = usedAssistance(s2.main)

if aided1 && aided2          -> Tie                                  (both used assistance: draw)
if aided1 && !aided2         -> Win(displayName2, null)              (only s1 aided: s2 wins, no diff)
if !aided1 && aided2         -> Win(displayName1, null)              (only s2 aided: s1 wins, no diff)
// neither aided
if t1 == t2                  -> Nuke
if t1 < t2                   -> Win(displayName1, formatMmSs(t2 - t1))
else                         -> Win(displayName2, formatMmSs(t1 - t2))
```
where `displayName = Boolean.TRUE.equals(main.getDuo()) ? name + " et al." : name`

Differentials are intentionally suppressed (`null`) when the loser was disqualified by assistance — the time gap is not meaningful in that case.

### Decision 8: Duo display name in win message
**Choice:** When the winning player has `duo = true`, append `" et al."` to their name before constructing `Win`, producing e.g. `"🏆 Alice et al. wins! (0:48)"`. This is handled entirely within `MainCrosswordScoreboard.determineOutcome` — no renderer changes, no new `BotText` constants.

**Rationale:** The `duo` flag is informational (does not affect win/loss determination) but does affect *attribution* — the win belongs to a team, not a solo player. Embedding the label in the name string is the simplest way to surface this: the renderer already interpolates `winnerName` directly into `SCOREBOARD_WIN_WITH_DIFF` / `SCOREBOARD_WIN_NO_DIFF`, so no further changes propagate.

**Scope:** Applies only when a clean win is determined by time (neither player used assistance). The `duo` flag on the *loser* is irrelevant to the win message and is already shown in the flags row. When a player wins due to the other using assistance (`Win(name, null)`), the duo display name still applies if the winner used duo.

### Decision 6: Pre-formatted differential label in `Win`
**Choice:** Change `Win(String winnerName, Integer differential)` to `Win(String winnerName, String differentialLabel)`. Each scoreboard impl formats before constructing `Win`; the renderer uses `SCOREBOARD_WIN_WITH_DIFF = "🏆 %s wins! (%s)"` (removing the hard-coded `-%d` prefix).

**Rationale:** `Win.differential` is currently an `Integer` (seconds for crosswords; score-point gap for Wordle/Strands). A single integer type cannot carry both a seconds-to-MM:SS format and a score format. Moving formatting responsibility to each scoreboard impl gives each game full control without coupling the renderer to game type. The renderer remains a simple dispatch with no game-type knowledge.

**Impact on other games:**
- **Wordle / Strands:** currently produce `Win(name, intDiff)` where `intDiff` is a score-point gap that is already dead code (never rendered, since `usesStreakDisplay()` is `true` and the outcome row is replaced by the streak row). After the change, the differential calculation is removed entirely and `Win(name, null)` is returned. Connections already returns `Win(name, null)` everywhere — no change needed.
- The type change is therefore fully contained to the crossword scoreboards and their tests.

**Change to `SCOREBOARD_WIN_WITH_DIFF`:** `"🏆 %s wins! (-%d)"` → `"🏆 %s wins! (%s)"`. The format specifier changes to `%s` since the label is now a pre-formatted string.

### Decision 7: MM:SS formatter
**Choice:** Add a package-private static helper `formatMmSs(int totalSeconds)` on `MainCrosswordScoreboard` (reused by Mini and Midi). Format: `String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)`. This produces `"0:48"`, `"1:03"`, etc., matching the existing `timeString` display style already used in the score-label row.

**Alternatives considered:**
- Add a utility to `BotText` — rejected; BotText holds display strings, not formatting logic.
- Add to a shared utility class — over-engineering for a one-liner; can be extracted later if needed.

### Decision 4: Nuke for Mini and Midi
**Choice:** `MiniCrosswordScoreboard` and `MidiCrosswordScoreboard` return `Nuke` whenever `t1 == t2` (replacing their current `Tie`). Neither has flags, so there is no "both aided" branch.

### Decision 5: New BotText constant
**Choice:** Add `public static final String SCOREBOARD_NUKE = "☢️ Nuke!";` (with a space between emoji and word, per user preference). Keep `SCOREBOARD_TIE` unchanged.

## Risks / Trade-offs

- **Risk:** Existing tests assert `ComparisonOutcome.Tie` for equal-time Mini/Midi cases. → **Mitigation:** Update those tests to expect `Nuke`; they live alongside the scoreboard classes and are part of the change scope.
- **Risk:** Suppressing the time differential in disqualified-Main-win cases may surprise users used to seeing `(-N)`. → **Mitigation:** Documented in the spec; the win is by rule, not by time, so showing a time gap would mislead.
- **Risk:** Changing `Win.differential` from `Integer` to `String` is a breaking type change touching all callers. → **Mitigation:** The only callers that ever passed a non-null differential were Wordle and Strands — both dead code since `buildResultMessage` is never reached when `usesStreakDisplay()` is true. Those callers are simplified to `null` as part of this change; no rendering regression is possible.
- **Risk:** Discord rendering of `☢️` on certain clients (very old mobile) may show as boxed glyph. → **Mitigation:** Acceptable; other emoji in `BotText` (e.g., 🏆, 🤝, 👫) already assume modern Discord clients.

## Migration Plan

No data migration. Behaviour change only; the next time a scoreboard refresh runs after deploy, the new outcomes are used. No feature flag needed — this is an opinionated rule change the team explicitly requested.
