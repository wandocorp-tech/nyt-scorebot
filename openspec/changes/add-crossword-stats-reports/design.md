## Context

The bot already persists daily crossword results in `Scoreboard` (one row per user per day, with embedded `MiniCrosswordResult`, `MidiCrosswordResult`, `MainCrosswordResult`). Win semantics for a single day are encapsulated in `MiniCrosswordScoreboard` / `MidiCrosswordScoreboard` / `MainCrosswordScoreboard`, each producing a `ComparisonOutcome` via `determineOutcome(sb1, name1, sb2, name2)`. The `WinStreakMidnightJob` adds the forfeit rule for solo submissions.

This design extends the existing data model with an *aggregation layer only*. No new persistent entities. No changes to how results are captured or compared. The new code reads what already exists and renders period summaries.

## Goals / Non-Goals

**Goals**

- Reuse the existing `ComparisonOutcome` semantics verbatim so that "wins this month" agrees with the daily win-streak record bookkeeping.
- Three scheduled cadences (weekly / monthly / yearly) plus an interactive `/stats` command with custom ranges.
- A dedicated stats Discord channel so the new posts do not pollute the results or status channels.
- Day-of-week breakdown for the Main crossword (NYT difficulty ramps Mon → Sat, with Sun as its own variant) on month-or-larger periods.
- Computation on demand. The data set is small enough (≤ ~3 puzzles/day × N players × N years), and on-demand keeps the design free of cache-invalidation risk.

**Non-Goals**

- Stats for Wordle, Connections, or Strands. Deferred to v2 in a separate change.
- Persisted stats snapshots / materialised views. Optional follow-up if `/stats period:all-time` becomes painful on the Pi.
- Image-rendered charts (PNG attachments). v1 stays in code-block text. v2 may revisit.
- Per-user filtering (e.g., `/stats user:@alice`). The pair structure of the bot makes this naturally a leaderboard, not a single-user view; revisit if requested.
- Live leaderboard updates between scheduled posts.

## Decisions

### Decision: Win attribution mirrors `crossword-win-streaks`

For each (date, game), aggregation calls the same `determineOutcome()` used by the win-streak system, plus the same forfeit rule used by `WinStreakMidnightJob`. The mapping of outcome → period-win is:

| Outcome / situation | Period-win awarded to |
|---|---|
| Clean `Win` | Winner |
| Solo submission (forfeit) | Lone submitter |
| `Win` with `duo=true` | No-one |
| `Tie` (Main, both used assistance) | No-one |
| `Nuke` (equal clean times) | No-one |
| Both missing | No-one |

This keeps the `/stats` win count and the win-streak record telling the same story.

**Rationale**: any divergence between "wins counted on the leaderboard" and "wins counted toward streaks" would be confusing and a perpetual source of bug reports.

**Alternatives considered**: a separate, simpler "fastest time wins" rule that ignores duo/tie/nuke. Rejected because it would silently disagree with the win-streak summary that's posted in the results channel every day.

### Decision: Anchor date is a hard configuration setting

`stats.anchor-date` must be explicitly set in `application.properties` (or via env). It defines the earliest date that any stats query will consider. If the property is missing:

- The scheduled jobs do not run (they log a warning and exit).
- `/stats` replies ephemerally with an error explaining how to enable the feature.

`/stats period:all-time` uses `max(stats.anchor-date, ScoreboardRepository.findEarliestDate())` as the lower bound — i.e., the anchor is a *floor*, not an override.

**Rationale**: making the anchor explicit means there is one canonical "stats started here" date that's consistent across reruns and across machines, including disaster-recovery restores. A `@PostConstruct` "set anchor to today if missing" would be silently wrong if the bot is ever started fresh on a backup database.

**Alternatives considered**:

- *No anchor, just use `findEarliestDate()`.* Rejected — historical scoreboard rows from before this feature shipped would be silently included, which the user explicitly chose against.
- *Auto-set on first start.* Rejected for the disaster-recovery reason above and because it makes test setup non-deterministic.

### Decision: Compute on demand, no materialised cache

Even at all-time, the aggregation is `O(days × players × 3 games)` with one DB round trip per date (or one batched range query, see "Repository" below). For a 5-year all-time window with 4 players and 3 games per day, that's ~20k row reads over a JPA fetch — well under a second on the Pi.

The trade-off: a "computing…" UX matters for the year/all-time/large-custom paths.

**Implementation**:

- All `/stats` invocations call `event.deferReply()` immediately (15-minute window).
- For `period` ∈ {`year`, `all-time`} or `custom` with `(to - from).days > 90`, the deferred reply is replaced with an *ephemeral confirmation* (two buttons: `Run report` / `Cancel`). On confirm, the actual computation runs and posts publicly.

**Alternatives considered**:

- *Materialise into a `CrosswordStatsSnapshot` table.* Rejected for v1 because of cache-invalidation complexity (results can be edited via `/duo`, `/check`, `/lookups` after the fact, which would invalidate any aggregated row that touched that date). Worth reconsidering only if profiling shows on-demand is too slow.

### Decision: Confirmation prompt wording

Per user request, the confirmation message must signal that the period is large *without* using the word "expensive":

> ⏳ This period covers a wider window. Computation may take a few seconds — proceed? *(auto-cancels in 15 s)*

Buttons: `Run report` (primary) / `Cancel` (secondary). Custom IDs encode the requested options so the button handler can re-derive the parameters without server-side state: `stats-confirm:<game>:<period>:<from?>:<to?>` and `stats-cancel:<game>:<period>:<from?>:<to?>`.

### Decision: Confirmation prompt auto-cancels after 15 seconds

When `StatsCommandHandler` posts the ephemeral confirmation prompt, it SHALL also schedule a one-shot timer (15 seconds) that, if it fires before either button is clicked, edits the ephemeral message to "Cancelled (timed out)." and disables both buttons. A button click that arrives after the timeout SHALL be ignored (or, if it arrives first, it SHALL cancel the pending timer).

Implementation uses Reactor's `Mono.delay(Duration.ofSeconds(15))` chained off the original `event.editReply(...)` call. The pending `Disposable` is keyed by the interaction token and stored in a small in-memory `ConcurrentHashMap`; the button handler removes/disposes it on click. Bot restarts simply lose the pending timers — the user sees a stale prompt, clicks, and the button handler responds normally (the parameters are still valid because they're encoded in the custom ID).

**Rationale**: 15 seconds is short enough that a stale prompt does not linger but long enough for an attentive user to click. Aligning with Discord's natural ephemeral fade keeps the UX feeling instant.

**Alternatives considered**:

- *Rely solely on Discord's interaction-token expiry (~15 minutes).* Rejected — leaves stale buttons visible far too long.
- *Persist pending prompts in the database.* Rejected as overkill; losing a prompt on restart is harmless.

### Decision: Period semantics (closed intervals, prior-period framing)

| Period | Window |
|---|---|
| `week` | Last 7 days ending *yesterday* (i.e., the most recently completed Mon–Sun is what the *scheduled* Sunday job covers; the slash-command `week` covers `today - 7 .. today - 1`) |
| `month` | Last calendar month for the scheduled job; for `/stats`, `today.minusMonths(1) + 1day .. today.minusDays(1)` |
| `year` | Last calendar year for the scheduled job; for `/stats`, `today.minusYears(1) + 1day .. today.minusDays(1)` |
| `all-time` | `max(anchor-date, earliest-scoreboard-date) .. today.minusDays(1)` |
| `custom` | `from .. to` inclusive, both required, both clamped to `[anchor-date, today.minusDays(1)]` (a fully-pre-anchor range is rejected) |

All windows exclude *today* so that a partially-played day is never reported. The end date for the scheduled jobs is always the day before the job runs.

**Rationale**: Reporting "today, partial" results would be misleading and would change after the user has seen the report. Excluding today keeps every report stable from the moment it's published.

### Decision: Day-of-week sub-report (Main only, month+)

The Main report includes a 7-row sub-block when `period.length() >= 28 days` (so `month`, `year`, `all-time`, and sufficiently-large `custom` qualify; `week` does not — only one Saturday per week is too noisy to report).

Each row shows, per player, the average solve time on that weekday in the window (played days only). Days where the player did not play are skipped from their column. Days where neither player played are simply absent from the row count.

```
🥇 MAIN — Day-of-Week Avg
    Mon   alice 4:12 (4)   bob 5:01 (4)
    Tue   alice 6:30 (3)   bob 7:15 (4)
    …
    Sun   alice 22:10 (4)  bob 18:45 (4)
```

The parenthetical is the played-day count for that weekday in the window, included so a "1-sample" Tuesday is visibly distinguishable from a representative average.

### Decision: Time formatting

All times use `m:ss` for sub-hour and `h:mm:ss` for ≥ 1 hour. Average times round to the nearest second. This matches the existing time-string format produced by `CrosswordParser`.

### Decision: Channel routing

- Scheduled jobs post into `discord.statsChannelId`. If unset, jobs log a warning and skip.
- `/stats` *also* posts into `discord.statsChannelId`, even when invoked from another channel, so all reports accumulate in one searchable place. The slash-command reply itself is a brief ephemeral acknowledgement ("Posted to <#stats-channel>"). This matches the v1 user choice that all reports are public.

**Alternative considered**: post the `/stats` result inline in the channel where it was invoked. Rejected because the user explicitly asked for one dedicated stats channel.

## Risks / Trade-offs

- **All-time performance on the Pi.** Mitigated by the confirmation prompt (user-controlled) and `deferReply` (Discord-side timeout extension). If aggregation creeps past a few seconds, add a `@Cacheable` layer keyed on `(period, anchor, today)` for window-relative queries — Spring's `ConcurrentMapCacheManager` is enough.
- **Disagreement with the daily win-streak summary.** Mitigated by reusing the same comparator code, not re-implementing the rules. A future change to win semantics will automatically flow through stats; the test suite asserts the agreement on a representative sample.
- **Property-driven anchor introduces a startup failure mode.** Mitigated by treating "missing anchor" as *feature disabled* (warn + skip), not as a startup failure — the rest of the bot is unaffected.
- **Day-of-week sub-report on small samples.** Mitigated by the `(n)` count next to each average and by gating on `period.length() >= 28 days`.
- **Forfeit attribution requires iterating dates with at least one submission.** A range query with `WHERE date BETWEEN :from AND :to` returns only dates with data, which is correct — dates with no submissions contribute nothing. Mitigated implicitly.
- **Confirmation buttons go stale if Discord times out.** Custom-ID-encoded parameters mean we don't keep server-side state; a stale click simply re-runs the same query. Mitigated.

## Migration Plan

No data migration. Hibernate `ddl-auto=update` is unaffected (no schema change). Operationally:

1. Set `stats.anchor-date=<deploy-date>` in `application.properties` (and env override on the Pi).
2. Set `discord.statsChannelId=1499773732489793738` in `application.properties` (and env override on the Pi).
3. Deploy. The slash command registers on startup; scheduled jobs activate on their next cron tick.
4. The first scheduled report will land Sunday 21:00 (weekly), 1st of the next month at 09:00 (monthly), or next Jan 1 at 09:00 (yearly), whichever comes first.

If `stats.anchor-date` is not set, the bot logs a warning on startup and the feature is silently disabled — no impact on existing functionality.

## Open Questions

<!-- All resolved during proposal review:
     • Auto-cancel timeout: 15 seconds (see "Confirmation prompt auto-cancels after 15 seconds").
     • Single-day custom range: accepted (see "Single-day range is accepted" scenario in the spec).
-->
None.
