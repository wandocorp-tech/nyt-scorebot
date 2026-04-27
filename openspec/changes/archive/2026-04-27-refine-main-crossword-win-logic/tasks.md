## 1. Domain & shared types

- [x] 1.1 Add `SCOREBOARD_NUKE = "☢️ Nuke!"` constant in `nyt-scorebot-domain/.../BotText.java` (alongside `SCOREBOARD_TIE`).
- [x] 1.2 Update `SCOREBOARD_WIN_WITH_DIFF` in `BotText.java` from `"🏆 %s wins! (-%d)"` to `"🏆 %s wins! (%s)"` (format specifier becomes `%s`; callers now own the sign and format).
- [x] 1.3 Change `Win(String winnerName, Integer differential)` to `Win(String winnerName, String differentialLabel)` in `ComparisonOutcome.java` and add `Nuke` record variant; update the sealed `permits` clause to include `Nuke`.
- [x] 1.4 Update `ScoreboardRenderer.buildResultMessage` to: (a) handle the new `Nuke` branch returning `BotText.SCOREBOARD_NUKE`, and (b) update the `Win` branch to pass `w.differentialLabel()` (String) directly into `SCOREBOARD_WIN_WITH_DIFF` (already `%s`).
- [x] 1.5 Simplify `WordleScoreboard.determineOutcome`, `StrandsScoreboard.determineOutcome`, and `ConnectionsScoreboard.determineOutcome` to return a dummy `new ComparisonOutcome.Tie()` (since the outcome is never rendered when `usesStreakDisplay() = true`). Remove all game-specific calculations (attempts, hints, mistakes, perfect logic).

## 2. Scoreboard logic

- [x] 2.1 Add a package-private static helper `formatMmSs(int totalSeconds)` in `MainCrosswordScoreboard`: `String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)`.
- [x] 2.2 In `MiniCrosswordScoreboard.determineOutcome`, return `new ComparisonOutcome.Nuke()` when `t1 == t2`; otherwise return `Win(fasterName, formatMmSs(diff))` using the helper from `MainCrosswordScoreboard` (or inline it).
- [x] 2.3 In `MidiCrosswordScoreboard.determineOutcome`, same as 2.2.
- [x] 2.4 Add a private static helper `usedAssistance(MainCrosswordResult r)` in `MainCrosswordScoreboard` that returns true iff `Boolean.TRUE.equals(r.getCheckUsed())` or `r.getLookups() != null && r.getLookups() > 0`.
- [x] 2.5 Rewrite `MainCrosswordScoreboard.determineOutcome` using the decision tree in `design.md` §Decision 3: both-aided → `Tie`; one-aided → `Win(displayName(otherPlayer), null)`; neither aided & equal time → `Nuke`; neither aided & unequal → `Win(displayName(faster), formatMmSs(diff))`. Where `displayName` appends `" et al."` to the player name when their `MainCrosswordResult` has `duo = true`.

## 3. Tests

- [x] 3.1 Update `WordleScoreboardTest`, `StrandsScoreboardTest`, and `ConnectionsScoreboardTest`: since `determineOutcome` now returns a dummy `Tie()` regardless of input, simplify tests to verify the method returns `Tie()` (no need to assert on game-specific state like attempts, hints, or mistakes).
- [x] 3.2 Update existing `MiniCrosswordScoreboard` tests asserting `Tie` for equal times to assert `Nuke`; update win-diff assertions from integer seconds to MM:SS strings.
- [x] 3.3 Update existing `MidiCrosswordScoreboard` tests the same way.
- [x] 3.4 Replace/expand `MainCrosswordScoreboard` outcome tests to cover all cases: (a) no-flags lower time wins with MM:SS diff, (b) no-flags equal time → Nuke, (c) only player1 used check → player2 wins null diff, (d) only player2 used lookups → player1 wins null diff, (e) both used assistance → Tie regardless of times, (f) duo-only on one side does not change a normal time-based win outcome, (g) winner has `duo = true` on time-based win → winnerName is "[name] et al.", (h) winner has `duo = true` when other player is disqualified by assistance → winnerName is "[name] et al.".
- [x] 3.5 Add a `ScoreboardRenderer` test verifying the `Nuke` outcome renders `BotText.SCOREBOARD_NUKE` in the result row.
- [x] 3.6 Run unit tests: `mvn test -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` and ensure all pass.
- [x] 3.7 Run `mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` to confirm JaCoCo coverage thresholds (≥80% instruction + branch) still hold.

## 4. Integration tests

- [x] 4.1 Update `EndToEndTest` to add three new crossword scenarios: (1) Mini with different times (time-based win outcome), (2) Midi with identical times (Nuke! outcome), (3) Main where both players used checks/lookups (draw outcome). Verify each scenario renders the correct outcome message and MM:SS differential format (where applicable).

## 5. Docs

- [x] 5.1 If `README.md` documents the Main crossword win rule or differential format, update it. Otherwise skip.
