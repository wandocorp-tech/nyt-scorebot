## 1. Database layer

- [x] 1.1 Create `WinStreak` entity in `nyt-scorebot-database` with fields: `id`, `user` (ManyToOne), `gameType` (enum, restricted to MINI/MIDI/MAIN_CROSSWORD), `currentStreak`, `baseStreak`, `baseDate`, `computedDate`, `lastUpdated`. Apply unique constraint on (`user_id`, `game_type`).
- [x] 1.2 Create `WinStreakRepository` (Spring Data JPA) with finder methods: `findByUserAndGameType(User, GameType)` and `findAllByUserIdIn(Collection<Long>)`.
- [x] 1.3 Add `WinStreak` and its package to JaCoCo coverage exclusions if it's a pure JPA entity (mirror existing `Streak` exclusion treatment).

## 2. Win streak service (snapshot recompute logic)

- [x] 2.1 Create `WinStreakService` in `nyt-scorebot-service` with a single public method that accepts a `GameType`, both `User`s, and a `ComparisonOutcome`, and performs the snapshot-then-apply update for both players.
- [x] 2.2 Implement the snapshot step: if `computedDate != today`, copy `currentStreak → baseStreak`, `computedDate → baseDate`, set `computedDate = today`.
- [x] 2.3 Implement outcome → action mapping per the spec table (Clean Win, Duo Win, Tie, Nuke, WaitingFor, Missing).
- [x] 2.4 Implement the gap-detection rule: `current = base + 1` if `today - baseDate == 1`, else `current = 1` for a Win.
- [x] 2.5 Resolve duo winners back to the underlying `User` via the `Scoreboard.user` relation, not by parsing display names.

## 3. Crossword outcome adapter

- [x] 3.1 Create `CrosswordWinStreakService` (or extend `WinStreakService` with a higher-level method) that, given a date and both `Scoreboard`s, calls each crossword scoreboard's `determineOutcome()` and forwards each outcome to `WinStreakService`.
- [x] 3.2 Wire it to handle Mini, Midi, and Main scoreboards independently — a missing game on one scoreboard must not block updates for the others.

## 4. Summary message rendering

- [x] 4.1 Create `WinStreakSummaryBuilder` in `nyt-scorebot-service` that, given the two players' `WinStreak` rows for all three crosswords, produces the code-block table string.
- [x] 4.2 Add 🔥 emoji decoration for `currentStreak >= 3`; render `0` plainly.
- [x] 4.3 Add `BotText` constants for the summary header, column labels, and any formatting tokens used by the builder.

## 5. Discord channel integration

- [x] 5.1 Add a `WIN_STREAK_SUMMARY` slot to the per-day message ID map in `ResultsChannelService` so the summary message can be re-edited (today) or re-posted (next day).
- [x] 5.2 In `ResultsChannelService.refresh()`, after both players are finished and before posting the scoreboards (or immediately after — order does not affect correctness, only display latency), call `CrosswordWinStreakService` for all three crosswords, then post the summary message using the new builder.
- [x] 5.3 In `ResultsChannelService.refreshGame(Main)`, after the Main scoreboard refresh, call `CrosswordWinStreakService` for Main only and edit the existing `WIN_STREAK_SUMMARY` message in place.
- [x] 5.4 Verify `FlagReplyHelper.refreshMainCrossword()` requires no changes (it already calls `refreshGame(Main)`).

## 6. Midnight rollover

- [x] 6.1 Create `WinStreakMidnightJob` (`@Component` with `@Scheduled(cron = "0 0 0 * * *", zone = "<puzzle TZ>")`) in `nyt-scorebot-discord` (or wherever it can reach both `WinStreakService` and `ResultsChannelService`).
- [x] 6.2 In the job, for each crossword game type and each known player pair, look up yesterday's scoreboards, classify as both-submitted / one-submitted / neither-submitted, and invoke `WinStreakService` accordingly. Skip the both-submitted case.
- [x] 6.3 After applying forfeits, edit yesterday's `WIN_STREAK_SUMMARY` message in place via `ResultsChannelService` to show the finalized values.
- [x] 6.4 Ensure the job uses the same timezone as `PuzzleCalendar` so the day boundary aligns with the puzzle calendar's anchor dates.

## 7. Tests

- [x] 7.1 Unit tests for `WinStreakService`: snapshot on first update of day, no overwrite on same-day re-run, all outcome → action transitions, gap detection, duo winner resolution.
- [x] 7.2 Specific regression test for the duo-after-finish scenario: simulate refresh() with `duo=null`, verify winner goes to base+1; simulate `/duo` then refreshGame(Main), verify winner restored to base.
- [x] 7.3 Unit tests for `WinStreakSummaryBuilder`: 🔥 threshold, plain 0, mixed values across the three crosswords.
- [x] 7.4 Unit tests for `WinStreakMidnightJob`: solo-submitter forfeit awards a win, neither-submitted resets both, both-submitted is a no-op, snapshot is preserved across the day's `WaitingFor` runs into the midnight finalization.
- [x] 7.5 Update `ResultsChannelServiceTest` to verify the summary message is posted after the six scoreboards on `refresh()` and edited (not re-posted) on `refreshGame(Main)`.
- [x] 7.6 Verify the existing `StreakService`/`/streak` tests still pass unchanged.

## 8. Verification

- [x] 8.1 Run `mvn test -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` and confirm all unit tests pass.
- [x] 8.2 Run `mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` and confirm JaCoCo coverage (≥80% instruction + branch) passes with the new code.
- [x] 8.3 Manual smoke check on a dev Discord channel: submit both players' crosswords, observe summary message; toggle `/duo`, observe summary edit; simulate a forfeit by leaving one game unsubmitted overnight, observe midnight finalization.
