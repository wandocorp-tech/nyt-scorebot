## 1. Diagnose the midnight silence

- [ ] 1.1 Run `SELECT id, name, channel_id, discord_user_id FROM app_user;` on the production database and record the results in `design.md` under Open Questions — **blocked: no access to the Pi from the dev environment**
- [ ] 1.2 Grep the service log around 00:00 for `Force-posting results`, `rolled over`, and `Error writing results`; record which lines are present
- [ ] 1.3 Confirm no `app_user` row has a null `discord_user_id` — **no longer a prerequisite: keying on the `NOT NULL UNIQUE` `channel_id` removes the dependency (revised D5)**
- [ ] 1.4 Resolve design.md decision D5 — confirm or replace the name-coupling hypothesis based on 1.1–1.3, and update Open Questions — **the fix is defensive and lands regardless; the new logging will identify the real guard on the first 00:00 run**

## 2. Observability (unblocks confirming the fix)

- [x] 2.1 Add a log line to every early return in `ResultsChannelService.prepareContext` — missing results channel (debug), both-finished gate (debug), fewer than two channels (warn)
- [x] 2.2 Log at warn in `refresh()` / `forceRefreshForDate()` when scoreboards exist for the date but `renderAll` returns an empty map, including the date and scoreboard count
- [x] 2.3 Log at info in `StatusBoardMidnightJob.forcePostYesterdayIfNeeded` when the force-post is skipped because results are already recorded as posted
- [x] 2.4 Add tests asserting each guard is logged (use an appender captor or assert via a spy on the collaborator that should not have been called)

## 3. Decouple player identity from display name

- [x] 3.1 Change `ResultsChannelService.prepareContext` to resolve scoreboards by `channel_id` (falling back to `discord_user_id`); keep `getName()` for rendering only — *keyed on `channel_id` rather than `discord_user_id`; see revised D5*
- [x] 3.2 Fall back to `discord_user_id` when `channel_id` does not match — *no name-based fallback: reintroducing it would restore the defect this change removes*
- [x] 3.3 Add a test proving a scoreboard still resolves when `app_user.name` differs from the configured channel name
- [x] 3.4 Add a test proving the rendered board uses the currently configured display name, not the persisted one
- [x] 3.5 Rename the `"William"` display-name literals to `"Will"` in `ConnectionsScoreboardTest` and `StrandsScoreboardTest`

## 4. Merge the midnight jobs into an ordered rollover

- [x] 4.1 Create `MidnightRolloverJob` with a single `@Scheduled(cron = "0 0 0 * * *", zone = "${discord.timezone:Europe/London}")` entry point
- [x] 4.2 Move the forfeit logic out of `WinStreakMidnightJob` into a plain collaborator method invoked as step 1 (no `@Scheduled`)
- [x] 4.3 Sequence steps: forfeits → force-post boards → evaluate crown → reset status board, each in its own try/catch with an error log
- [x] 4.4 Remove the `@Scheduled` annotations from `StatusBoardMidnightJob` and `WinStreakMidnightJob` so neither fires independently
- [x] 4.5 Change `WinStreakMidnightJob` player resolution to use `channel_id` (falling back to `discord_user_id`) with a debug log when a player is unregistered
- [x] 4.6 Add a test asserting forfeits complete before the render step (verify with an `InOrder` on the collaborators)
- [x] 4.7 Add tests asserting a throw in any one step does not prevent the remaining steps from running

## 5. Expose crossword outcomes for reuse

- [x] 5.1 Change `CrosswordWinStreakService.updateOne` to return its computed `ComparisonOutcome`
- [x] 5.2 Change `updateAll` to return `Map<GameType, ComparisonOutcome>` for Mini, Midi, and Main; keep the existing null-scoreboard early return returning an empty map
- [x] 5.3 Update existing `CrosswordWinStreakServiceTest` cases for the new return types
- [x] 5.4 Verify no behavioral change to `WinStreakService.applyOutcome` call sites

## 6. Triple crown detection

- [x] 6.1 Create `TripleCrownService` in `nyt-scorebot-service` taking the three outcomes plus both scoreboards and names, returning `Optional<String>` (the winning display name)
- [x] 6.2 Implement the sweep rule — all three outcomes are `Win` naming the same player
- [x] 6.3 Implement the forfeit rule — `Win(name, null)` counts identically to `Win(name, differential)`
- [x] 6.4 Implement the duo rule — resolve the winner's own scoreboard and block the crown when their `MainCrosswordResult.getDuo()` is `TRUE`; a loser's duo flag does not block
- [x] 6.5 Add a code comment on 6.4 recording that this asymmetry deliberately differs from `WinStreakService.applyOutcome`
- [x] 6.6 Test the split-wins, tie, nuke, and waiting-for cases all yield no crown
- [x] 6.7 Test the winner-duo blocks / loser-duo allows / null-duo allows cases
- [x] 6.8 Test that mixed differential and forfeit wins still award the crown
- [x] 6.9 Test that losing Wordle, Connections, and Strands does not affect the crown

## 7. Deletable conditional slot

- [x] 7.1 Add `delete(Snowflake channelId, Snowflake messageId)` to `MessageSlotWriter`, returning a `Mono<Void>` that swallows and logs an already-deleted message at warn
- [x] 7.2 Add a `deleteSlot(Snowflake channelId, String slot)` to `ResultsChannelService` that chains through the existing `slotChains` map so a delete cannot overtake an in-flight post
- [x] 7.3 Clear the slot's entry in `postedMessageIds` and `slotChains` after a successful delete
- [x] 7.4 Test that a delete issued while a post is in flight deletes the resulting message rather than orphaning it
- [x] 7.5 Test that deleting an untracked slot is a no-op
- [x] 7.6 Test that deleting an already-removed message clears the tracked ID without erroring

## 8. Wire the crown into the results flow

- [x] 8.1 Add a `__triple_crown__` slot constant to `ResultsChannelService`
- [x] 8.2 Add `postOrRevokeTripleCrown(ctx)` invoked after `postOrEditWinStreakSummary` in both `refresh()` and `forceRefreshForDate()`
- [x] 8.3 Post the crown message when a crown is awarded and no message is tracked; do nothing when one is already tracked for the same winner
- [x] 8.4 Delete the tracked message and clear the slot when no crown is awarded
- [x] 8.5 Clear the crown slot in `rolloverIfNewDay()` alongside `postedMessageIds` and `slotChains`
- [x] 8.6 Add crown copy to `BotText` — no display strings inline
- [x] 8.7 Invoke crown evaluation as step 3 of `MidnightRolloverJob`, after the force-post
- [x] 8.8 Test that the crown message is written after the win-streak summary write is issued
- [x] 8.9 Test post → revoke (delete) → re-earn (fresh post) across successive refreshes
- [x] 8.10 Test that the midnight path does not duplicate a crown already posted intra-day
- [x] 8.11 Test that a crown evaluation throw still allows the status board reset to run

## 9. V11 data migration

- [x] 9.1 Confirm the identity key for the affected player — *used `channel_id = 1358111788146622709` from `application.properties`, which needs no production access*
- [x] 9.2 Create `V11__fix_will_2026_05_17_crossword_and_seed_pbs.sql`; do not modify V8, V9, or V10
- [x] 9.3 Step 1 — correct the 2026-05-17 Main Crossword row to `time_string = '1:10:25'`, `total_seconds = 4225`, gated on `channel_id` and guarded so an already-correct row is untouched
- [x] 9.4 Step 2 — re-apply the V8 Main Crossword PB seeds (Monday–Sunday) for the player V8 missed, gated on `channel_id`, using the same `LEAST`-style non-regressing merge
- [x] 9.5 Step 3 — rebuild the affected `crossword_history_stats` bucket from `game_result`, setting `pb_seconds = LEAST(rebuilt_min, seeded_pb)` so the seeded Sunday PB is not regressed
- [x] 9.6 Derive `sample_count` and `sum_seconds` from qualifying `game_result` rows only (clean solves: no check, no lookups, no duo)
- [x] 9.7 Add a migration test that seeds the corrupted state (a 70s / `'1:10'` row plus the bogus bucket), runs the migration, and asserts `total_seconds = 4225` and the corrected bucket
- [x] 9.8 Add a test asserting the seeded Sunday PB survives the rebuild
- [x] 9.9 Add a test asserting the migration is a no-op against an already-corrected database
- [x] 9.10 Add a test asserting the migration is a no-op against a database that never held the bad row

## 10. Verify and document

- [x] 10.1 Run `mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` and confirm the JaCoCo 80% instruction and branch thresholds still pass
- [x] 10.2 Confirm new business logic in `service/` and `discord/` is covered; add tests for any gap the coverage report flags
- [x] 10.3 Update `README.md` if the triple crown is user-facing behavior worth documenting
- [ ] 10.4 Deploy, then verify the corrected 2026-05-17 time and Sunday PB via `/stats`
- [ ] 10.5 Verify the first 00:00 run after deploy emits the new log lines and posts boards
- [x] 10.6 Commit each of sections 2–4 (midnight fix), 5–8 (triple crown), and 9 (data migration) as separate conventional commits
