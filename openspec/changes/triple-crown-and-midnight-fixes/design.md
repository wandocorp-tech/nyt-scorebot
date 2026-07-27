## Context

Three defects and one feature share a single review because they all converge on `ResultsChannelService` and the two 00:00 scheduled jobs.

Current daily flow:

```
submission ──> MessageListener ──> ScoreboardService.saveResult
                                          │
                                          ▼
                            ResultsChannelService.refresh()
                                          │
                    prepareContext(today, force=false)
                     └─ gate: areBothPlayersFinishedToday()
                                          │
                    crosswordWinStreakService.updateAll(...)
                    scoreboardRenderer.renderAll(...)  ──> writeSlot per game
                    postOrEditWinStreakSummary(...)    ──> writeSlot summary

00:00 Europe/London, single-threaded scheduler pool, UNDEFINED ORDER:
   ┌─ StatusBoardMidnightJob ──┐        ┌─ WinStreakMidnightJob ────┐
   │ forcePostYesterdayIfNeeded│        │ applyForfeit x3           │
   │ resetStatusBoard          │        │ editSummaryIfPresent      │
   └───────────────────────────┘        └───────────────────────────┘
```

Two structural weaknesses drive most of this change.

**Name coupling.** `ScoreboardService.findOrCreateUser` writes `app_user.name` from `discord.channels[n].name`, a config value with an env override (`DISCORD_CHANNEL_0_NAME`, defaulting to `Will`). Three places then treat that mutable string as a join key:

- `ResultsChannelService.prepareContext` — `Collectors.toMap(sb -> sb.getUser().getName(), ...)` then `byName.get(channels.get(0).getName())`
- Migrations V8, V9, V10 — `WHERE u.name = 'William'`

The migrations are proven broken: they match zero rows in production and Flyway records them as `SUCCESS` regardless, because a zero-row `UPDATE` is not an error. `FlywayMigrationTest` runs against an empty database, where zero-rows-matched is indistinguishable from correct behavior, so nothing failed.

**Silent empty renders.** When `byName` lookups miss, `sb1` and `sb2` are both null, every `ScoreboardRenderer.render` call returns `Optional.empty()` (the `!has1 && !has2` guard), `renderAll` returns an empty map, the `writeSlot` loop body never executes, and `postOrEditWinStreakSummary` returns early on `sb1 == null`. Zero Discord calls, zero log lines. Indistinguishable from "nothing to post."

## Goals / Non-Goals

**Goals:**

- Detect and celebrate a triple crown, with correct handling of duo and forfeit edge cases, and revoke it when a flag change invalidates it.
- Make the midnight force-post actually publish, and make every failure mode of that path observable in the service log.
- Eliminate display name as a join key in both application code and data migrations.
- Correct the 2026-05-17 record and the personal-best data it corrupted, without regressing manually-seeded PBs.

**Non-Goals:**

- Persisting slot message IDs across restarts. In-memory tracking is accepted; a restart may re-post the crown.
- Extending the crown to Wordle, Connections, or Strands.
- Backfilling or re-parsing any other historical rows. V11 is scoped to the known-bad 2026-05-17 record and the V8 seeds it interacts with.
- Retroactively awarding crowns for past days.
- Changing the existing `ComparisonOutcome` semantics or the win-streak algorithm.

## Decisions

### D1 — Triple crown is derived from `ComparisonOutcome`, not recomputed

`CrosswordWinStreakService.updateOne` already computes exactly the signal needed: a `ComparisonOutcome` per crossword. A new `TripleCrownService` consumes the three outcomes rather than re-deriving winners from raw times.

```
MINI ─┐
MIDI ─┼─> ComparisonOutcome ∈ {Win(name, diff), Tie, Nuke, WaitingFor}
MAIN ─┘
             │
             ▼
   all three are Win with the same winnerName?
             │  yes
             ▼
   winner's Main result has duo != true?
             │  yes
             ▼
        TRIPLE CROWN for winnerName
```

*Alternative considered:* recompute winners inside the crown service from `total_seconds`. Rejected — it would duplicate the disqualification, tie-break, and failure-handling logic already encoded in each `GameComparisonScoreboard`, and the two implementations would drift.

*Consequence:* `CrosswordWinStreakService.updateOne` currently discards the outcome after passing it to `WinStreakService`. It must return the outcomes so `updateAll` can hand back a `Map<GameType, ComparisonOutcome>`.

### D2 — Duo blocks the crown only when it is the winner's flag

Per explicit product decision: a loser's duo does not diminish the winner's achievement, but a winner who solved the Main Crossword collaboratively has not swept it alone.

`CrosswordWinStreakService.isDuo` already reads the flag per scoreboard. The crown check resolves the winner's name to their scoreboard and inspects only that side's `MainCrosswordResult.getDuo()`.

Note the asymmetry with `WinStreakService.applyOutcome`, which receives both `duo1` and `duo2`. The crown rule is deliberately narrower; this is a divergence and must be spelled out in the spec so a future reader does not "fix" it into consistency.

### D3 — Forfeit wins count; `Win(name, null)` is a full win

`ComparisonOutcome.Win` carries a null `differentialLabel` when there is no time gap to show — the opponent failed, was disqualified, or did not submit. The crown treats `Win(X, null)` identically to `Win(X, "+1:23")`.

This means a day where the opponent no-showed all three crosswords yields a crown. Accepted per product decision.

### D4 — Crown is evaluated after midnight forfeits, which forces job ordering

Consequence of D3. During the day, a non-submission is `WaitingFor`, not a win. Only `WinStreakMidnightJob.applyForfeit` converts non-submission into a forfeit. So for the crown to fire on a forfeit sweep, forfeits must be applied *before* the midnight render.

Both jobs currently use `@Scheduled(cron = "0 0 0 * * *")` on the default single-threaded scheduler. Execution is serialized but the order is an accident of bean registration.

**Decision: merge the two jobs into one ordered `MidnightRolloverJob`** with an explicit internal sequence:

```
1. WinStreakMidnightJob logic  — applyForfeit for Mini, Midi, Main
2. force-post yesterday's boards (if not already posted)
3. evaluate + post triple crown for yesterday
4. reset status board for the new day
```

*Alternatives considered:*
- `@Order` on the beans — does not influence `@Scheduled` trigger order; misleading.
- Offset crons (`0 0 0` and `0 1 0`) — creates a visible one-minute window of inconsistent state and is fragile under clock drift or a slow first job.
- `@DependsOn` — controls bean construction order, not scheduled execution.

Merging keeps each job's logic in its existing collaborator classes; the new job only sequences them. Each step retains its own try/catch so one failure cannot suppress the rest.

*Note:* `WaitingFor` still blocks the crown when a game was genuinely never resolved, because forfeit application changes the streak state but the render path re-derives outcomes from the scoreboards. Step 1 must therefore make non-submission observable to step 3 — the crown evaluation for a past date treats "opponent has no result for game G and the date is closed" as a forfeit win, matching what step 1 recorded.

### D5 — Resolve scoreboards by `channel_id`, not by name

`prepareContext` switches from a name-keyed map to an ID-keyed lookup. Display names remain in use for *rendering* only.

**Revised during implementation.** The original decision named `discord_user_id` as the key. Inspecting the V1 baseline showed that column is **nullable** (V3 merely renamed it from `user_id`), whereas `channel_id` is `NOT NULL UNIQUE` and is already the identity `ScoreboardService.findOrCreateUser` resolves on. `channel_id` is therefore the stronger key and is tried first, with `discord_user_id` retained as a fallback for any row whose channel has been reconfigured. The same two-step `resolveUser` is used in `WinStreakMidnightJob`.

This is the most likely root cause of the midnight silence and it removes the whole defect class.

### D6 — Log every early return

Every `return null` / `return` in `prepareContext`, `forcePostYesterdayIfNeeded`, and the crown evaluation gets a log line stating which guard tripped and the relevant values. An empty `renderAll` result logs at `warn` when scoreboards for the date exist but nothing rendered — the exact signature of the D5 defect.

This is required to close out the diagnosis: production evidence is still needed to confirm whether the failure is D5, the app being down at 00:00, or `hasPostedResultsForDate` returning true.

### D7 — Crown message is a deletable slot, breaking the lifecycle invariant

`results-message-lifecycle` currently asserts every slot is created once and edited forever. The crown is conditional, so it needs a third state.

```
        ┌──────────┐   crown earned    ┌──────────┐
        │ NO SLOT  │ ────────────────> │  POSTED  │
        └──────────┘                   └──────────┘
             ▲                              │
             │   crown no longer earned     │
             └──────────────────────────────┘
                    delete message,
                    clear slot id
```

`MessageSlotWriter` gains a `delete(channelId, messageId)` that tolerates an already-deleted message. Deletion is chosen over editing to a "revoked" string per explicit product decision — a revoked crown should leave no trace.

The delete must join the same per-slot `slotChains` serialization used by `writeSlot`, or a delete can race ahead of an in-flight post and orphan the message.

### D8 — V11 supersedes V8/V9/V10 rather than amending them

V8, V9, and V10 are applied in production. Flyway stores a checksum per applied migration; editing any of them makes `flyway validate` fail at startup and the application will not boot. They stay frozen.

V11 re-does all three jobs, gated on `channel_id` (see D5):

1. Correct the 2026-05-17 Main Crossword row to `1:10:25` / `4225`, guarded so an already-correct row is untouched (idempotent).
2. Re-apply the V8 Main PB seeds for the user V8 missed.
3. Rebuild the affected `crossword_history_stats` bucket from `game_result` truth, using `LEAST(rebuilt_min, seeded_pb)`.

*The `LEAST` guard matters:* V9/V10 step 2 set `pb_seconds = MIN(total_seconds)` from recorded results alone. The Sunday PB of 1466s predates the bot and has no `game_result` row, so a bare rebuild would silently replace a genuine PB with a slower recorded one. This bug is inherited from V9/V10 and must not be carried into V11.

*Migration test gap:* `FlywayMigrationTest` runs on an empty database and cannot catch a zero-row migration. V11 needs a test that seeds the corrupted state, runs the migration, and asserts the corrected values — otherwise this change repeats the exact failure it is fixing.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Root cause of the midnight silence is still unconfirmed; D5 is a hypothesis | D6 logging lands regardless and is independently valuable. Confirm against production `app_user` rows and service logs before closing the change. If D5 is wrong, the logging identifies the real guard. |
| Merging the two midnight jobs changes failure isolation — one long-running step could delay the rest | Each step keeps its own try/catch. Ordering is explicit and unit-testable, which the current implicit ordering is not. |
| Editing V8/V9/V10 would brick startup via checksum mismatch | Explicitly frozen. Only test-fixture `"William"` literals are renamed; no `.sql` file under `db/migration` with a version ≤ 10 is touched. |
| V11 rebuild regresses a manually-seeded PB | `LEAST(rebuilt_min, seeded_pb)`, plus a test asserting the seeded 1466s Sunday PB survives. |
| Crown re-posts as a duplicate after a mid-day restart | Accepted per product decision. In-memory tracking only. |
| Crown fires on a total opponent no-show, which may read as hollow | Accepted per product decision — forfeit wins count. |
| Delete racing an in-flight post orphans a crown message | Route deletes through the existing per-slot `slotChains` serialization. |
| Switching to an ID lookup breaks if the chosen column is null for legacy rows | Resolved by keying on `channel_id`, which the V1 baseline declares `NOT NULL UNIQUE` (`discord_user_id` is nullable). `discord_user_id` is kept as a secondary fallback. Covered by `WinStreakMidnightJobTest` and `ResultsChannelServiceTest`. |

## Migration Plan

1. **Diagnose.** Run `SELECT id, name, channel_id, discord_user_id FROM app_user;` on the Pi and review the service log around 00:00 for `Force-posting results`, `rolled over`, and `Error writing results`. Record the outcome in this document before implementing the midnight fix.
2. **Deploy code + V11 together.** Flyway runs on startup, before the first scheduled job.
3. **Verify data.** Re-check the 2026-05-17 row and the Sunday PB via `/stats`.
4. **Verify midnight.** Confirm the first 00:00 run after deploy emits the new log lines and posts boards.
5. **Rollback.** Code rolls back by redeploying the previous JAR. V11 is *not* auto-reversible — it is idempotent and guarded, so re-running is safe, but undoing it requires a manual corrective migration. The data it writes is a correction to known-bad values, so rollback is not expected to be needed.

## Open Questions

- **Which failure mode is the midnight silence?** Blocking for the final shape of the fix. D5 is the leading hypothesis but unconfirmed. Resolve via step 1 of the migration plan.
- **Is 1466s a genuine Sunday PB to preserve?** Assumed yes (it appears in V8's manually-supplied seed list). If it was itself derived from corrupted data, the `LEAST` guard should be dropped.
- **What does the crown message say?** Copy lives in `BotText`; wording not yet decided.
- **Should the crown also be evaluated on the intra-day `refresh()` path**, or only at midnight? Intra-day gives immediate celebration but can post and then delete a crown as later results arrive. Midnight-only is stabler but delays the payoff. Current assumption: evaluate on both, relying on the delete path for revocation.
