## Why

The bot persists every daily crossword result (Mini, Midi, Main) and already knows how to determine a head-to-head winner via the existing `ComparisonOutcome` machinery. Today this data is only surfaced for *that day* — the daily scoreboards in the results channel and the rolling win-streak summary. Players have no way to see *who has actually won the most crosswords this week / month / year*, what their average solve times look like over time, or how their performance trends across the week (e.g., Saturday Main vs. Monday Main).

Adding period-aggregated reports — both pushed on a schedule and pulled via a slash command — turns the existing per-day data into a recurring source of bragging rights and self-comparison without changing how results are captured or displayed today.

V1 is intentionally scoped to crossword games only. Wordle / Connections / Strands stats are deferred to a v2.

## What Changes

- **Add a new dedicated stats Discord channel** (ID `1499773732489793738`) for posting both scheduled recaps and on-demand `/stats` results. The results channel and status channel are unchanged.
- **Add a configurable anchor date** (`stats.anchor-date`, default = the deploy date the property is first set to) that defines the earliest date considered by any stats query. Periods that would extend before the anchor are clamped to the anchor.
- **Add a `CrosswordStatsService`** (in `nyt-scorebot-service`) that, given a date window, iterates each day in the window, reuses the existing `MiniCrosswordScoreboard` / `MidiCrosswordScoreboard` / `MainCrosswordScoreboard` `determineOutcome()` logic plus the midnight forfeit semantics from `WinStreakMidnightJob` to attribute one of: clean win, forfeit win, duo (no win), tie (no win), nuke (no win), or no-submission, and aggregates per-user totals per game.
- **Add a `CrosswordStatsReportBuilder`** (in `nyt-scorebot-service`) that renders aggregate results into a code-block report. The report includes:
  - Per game (Mini / Midi / Main, or all three): a win leaderboard, average solve time (played days only), best solve time + date, and participation count.
  - For Main only, when the period is `month` or larger: a dedicated day-of-week breakdown block showing average solve time per weekday per player.
- **Add three scheduled report jobs** in `nyt-scorebot-discord`:
  - `WeeklyCrosswordStatsJob` — Sunday 21:00 in the puzzle timezone, posts the previous 7-day report (Mon–Sun).
  - `MonthlyCrosswordStatsJob` — 1st of each month, 09:00 in the puzzle timezone, posts the previous calendar-month report.
  - `YearlyCrosswordStatsJob` — 1 January, 09:00 in the puzzle timezone, posts the previous calendar-year report.
  All three post into the new stats channel and gracefully no-op (with a logged message, no exception) if no data exists in the window.
- **Add a `/stats` slash command** with options:
  - `game` (required): one of `mini`, `midi`, `main`, `all`.
  - `period` (required): one of `week`, `month`, `year`, `all-time`, `custom`.
  - `from` and `to` (`YYYY-MM-DD` strings, required when `period=custom`, rejected otherwise): inclusive date range, must satisfy `from <= to`, and `to` must not be in the future. The range is clamped to the anchor date; a fully-pre-anchor range is rejected with an ephemeral error.
  - For `period` ∈ {`year`, `all-time`} or a `custom` range spanning more than 90 days, the bot first replies with an **ephemeral confirmation prompt** (two buttons: "Run report" / "Cancel") explaining that this period is large and may take longer to compute. After confirmation, the report is computed (using `deferReply` to extend the interaction window) and posted publicly to the stats channel.
  - For shorter periods, the report is computed immediately and posted publicly.
- **Reuse existing win semantics, exactly.** Wins in a period are attributed using the same `ComparisonOutcome` rules as `crossword-win-streaks`:
  - Clean Win → +1 to the winner.
  - Forfeit (only one player submitted that day for that game) → +1 to the lone submitter.
  - Duo Win, Tie, Nuke, both-missing → no win attributed.
- **Compute on demand** with no materialised cache. Aggregation iterates `ScoreboardRepository.findAllByDateWithUser(date)` for each date in the window. If profiling later shows this is slow on the Pi for `all-time`, a cached `CrosswordStatsSnapshot` table can be added in a follow-up change without touching the public API.

## Capabilities

### New Capabilities

- `crossword-stats-reports`: Period-aggregated crossword statistics — leaderboards, average and best solve times, participation, and Main day-of-week breakdowns — exposed via three scheduled jobs (weekly / monthly / yearly) that post into a dedicated stats channel and via a `/stats` slash command supporting `week`, `month`, `year`, `all-time`, and `custom` date ranges with a confirmation step for large windows.

### Modified Capabilities

<!-- None — crossword-scoreboards, crossword-win-streaks, results-message-lifecycle, multi-channel-monitoring, and user-scoreboard-persistence are all unchanged. The new code reads existing scoreboard rows and reuses (does not modify) the existing ComparisonOutcome logic. -->

## Impact

- **New service**: `CrosswordStatsService` (in `nyt-scorebot-service`) computes per-period aggregates by reusing the existing crossword `Scoreboard` comparators and forfeit logic.
- **New helper**: `CrosswordStatsReportBuilder` (in `nyt-scorebot-service`) renders the code-block report. Includes a separate Main day-of-week sub-report enabled for `month`+ periods.
- **New repository method**: `ScoreboardRepository.findAllByDateBetweenWithUser(LocalDate from, LocalDate to)` (range variant of the existing `findAllByDateWithUser`) plus `findEarliestDate()` (for `all-time` lower bound).
- **New configuration property**: `stats.anchor-date` (`LocalDate`, no default — the property must be explicitly set in `application.properties` for the feature to activate; if unset, the feature logs a warning at startup and disables `/stats` plus the scheduled jobs).
- **New configuration property**: `discord.statsChannelId` (`String`, optional — when unset, scheduled jobs are disabled and `/stats` replies ephemerally with an error).
- **New scheduled jobs**: `WeeklyCrosswordStatsJob`, `MonthlyCrosswordStatsJob`, `YearlyCrosswordStatsJob` (all `@Component` + `@Scheduled` in `nyt-scorebot-discord`).
- **New slash command**: `/stats` registered via `SlashCommandRegistrar`, handled by a new `StatsCommandHandler` (`@Component` implementing `SlashCommandHandler`), with a confirmation button flow handled by a new `StatsConfirmationButtonHandler` listening to `ButtonInteractionEvent` for `stats-confirm:*` / `stats-cancel:*` custom IDs.
- **`BotText`**: New constants for the `/stats` command name, option names and descriptions, the confirmation prompt text, the report header lines, day-of-week labels, and error messages.
- **No changes** to existing entities, the `Scoreboard` schema, `WinStreakService`, `StreakService`, the existing scoreboards, the results channel, or the status channel.
- **JaCoCo**: New service and builder are subject to the existing 80% coverage threshold; new entity-only or DTO-only files (if any) are added to exclusions.
- **Tests**: New unit tests cover `CrosswordStatsService` (win attribution mirrors `crossword-win-streaks` semantics including forfeit, anchor clamping, all-time lower bound, day-of-week bucketing, played-days-only averaging), `CrosswordStatsReportBuilder` (per-game block, all-game block, day-of-week block enabled for month+, empty-period rendering), `StatsCommandHandler` (option validation, custom range validation, confirmation flow for large periods, missing-channel error path), and each scheduled job (cron timing assertions, no-op on empty window, posts into the configured stats channel).
