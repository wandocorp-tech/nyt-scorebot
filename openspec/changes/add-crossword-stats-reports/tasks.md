crossword-stats-reports

## 1. Configuration

- [x] 1.1 Add `stats.anchor-date` (`LocalDate`) and `discord.statsChannelId` (`String`) to `application.properties` with placeholder env-var bindings (`${STATS_ANCHOR_DATE:}` and `${DISCORD_STATS_CHANNEL_ID:}`).
- [x] 1.2 Create a `StatsProperties` `@ConfigurationProperties("stats")` POJO in `nyt-scorebot-app` (or `nyt-scorebot-service` if it's needed there) with field `anchorDate` and a derived `boolean isEnabled()`.
- [x] 1.3 Extend `DiscordChannelProperties` (or add a sibling property) to expose `statsChannelId`.
- [x] 1.4 Document both properties in `README.md` under the configuration section.

## 2. Repository layer

- [x] 2.1 Add `ScoreboardRepository.findAllByDateBetweenWithUser(LocalDate from, LocalDate to)` returning all `Scoreboard` rows in the (inclusive) range with the `User` fetched eagerly.
- [x] 2.2 Add `ScoreboardRepository.findEarliestDate()` returning `Optional<LocalDate>` of the minimum `date` across all rows.
- [x] 2.3 Verify a covering index on `Scoreboard.date` exists (Hibernate generates one for the unique constraint on `(user_id, date)`; confirm via `show columns` in the H2 console or by inspecting `ddl-auto` output). Add an explicit `@Index` if needed.

## 3. Stats service (computation)

- [x] 3.1 Create `CrosswordStatsService` in `nyt-scorebot-service`. Public method: `CrosswordStatsReport compute(GameTypeFilter games, LocalDate from, LocalDate to)`.
- [x] 3.2 Implement `GameTypeFilter` (sealed/enum) covering `Mini`, `Midi`, `Main`, and `All`.
- [x] 3.3 Implement window iteration: load all in-range scoreboards via `findAllByDateBetweenWithUser`, group by date, then for each `(date, game)` invoke the matching `*CrosswordScoreboard.determineOutcome()` (or apply forfeit rules if exactly one player submitted).
- [x] 3.4 Implement win attribution table per the proposal (Clean Win / Forfeit / Duo / Tie / Nuke / Missing) and accumulate per-user win counts per game.
- [x] 3.5 Compute average solve time per user per game across played days only (skip days where that user has no result for that game).
- [x] 3.6 Compute best solve time per user per game and capture the date of that best.
- [x] 3.7 Compute participation count per user per game (number of played days in window).
- [x] 3.8 For Main only, when `(to - from).days >= 28`, additionally compute day-of-week buckets: average solve time per `DayOfWeek` per user, plus the count per bucket.
- [x] 3.9 Build `CrosswordStatsReport` (immutable record / DTO) carrying the per-game blocks, optional Main day-of-week block, the resolved window, and the inputs the report was generated from.
- [x] 3.10 Anchor clamping: at the top of `compute(...)`, raise `from` to `anchor` if it precedes the anchor; if the entire window is pre-anchor, throw `StatsWindowBeforeAnchorException`.

## 4. Report builder (rendering)

- [x] 4.1 Create `CrosswordStatsReportBuilder` in `nyt-scorebot-service`. Public method: `String render(CrosswordStatsReport report)`.
- [x] 4.2 Render a code-block report header showing the period type and date range (`Apr 24 – 30, 2026`).
- [x] 4.3 For each requested game, render a leaderboard sorted by win count descending, ties broken by best time ascending.
- [x] 4.4 Show columns: rank (`🥇 / 🥈 / 🥉` for top three then plain numbers), player name, wins, average time, best time + date.
- [x] 4.5 Format times as `m:ss` for sub-hour and `h:mm:ss` for ≥ 1 hour. Round averages to the nearest second.
- [x] 4.6 Render the Main day-of-week sub-block when present (gated by service-side `>= 28 day` check), one row per weekday `Mon..Sun`, columns per player showing `m:ss (n)`.
- [x] 4.7 Render an empty-period placeholder when no submissions exist in the window (e.g., "No crossword results in this window.") rather than emitting an empty leaderboard.
- [x] 4.8 Add `BotText` constants for the report header, period labels (Weekly / Monthly / Yearly / All-Time / Custom), game section headers (Mini / Midi / Main), the day-of-week labels, and the empty-period message.

## 5. Stats channel posting

- [x] 5.1 Create `StatsChannelService` in `nyt-scorebot-discord` with `Mono<Void> post(String content)` that resolves the configured `statsChannelId`, fetches the `MessageChannel`, and posts the message. Logs a warning and no-ops if the channel ID is unset.
- [x] 5.2 Reuse the existing reactive Discord4J client wiring (mirror the pattern in `StatusChannelService`).

## 6. Scheduled jobs

- [x] 6.1 Create `WeeklyCrosswordStatsJob` (`@Component` + `@Scheduled(cron = "0 0 21 * * SUN", zone = "${discord.timezone:Europe/London}")`). The job computes `today.minusDays(7) .. today.minusDays(1)`, builds an "All games" report, and posts to the stats channel via `StatsChannelService`.
- [x] 6.2 Create `MonthlyCrosswordStatsJob` (`@Component` + `@Scheduled(cron = "0 0 9 1 * *", zone = "${discord.timezone:Europe/London}")`). Computes the previous calendar month and posts.
- [x] 6.3 Create `YearlyCrosswordStatsJob` (`@Component` + `@Scheduled(cron = "0 0 9 1 1 *", zone = "${discord.timezone:Europe/London}")`). Computes the previous calendar year and posts.
- [x] 6.4 All three jobs check `StatsProperties.isEnabled()` first and skip with a logged warning if not. They also catch and log any thrown exception without propagating (mirroring `StatusBoardMidnightJob.run()`).
- [x] 6.5 Each job exposes a package-private `void run()` for direct invocation in tests.

## 7. /stats slash command

- [x] 7.1 Register the `/stats` command in `SlashCommandRegistrar.buildAllCommands()` with options `game` (string, required, choices: mini / midi / main / all), `period` (string, required, choices: week / month / year / all-time / custom), `from` (string, optional, format `YYYY-MM-DD`), `to` (string, optional, format `YYYY-MM-DD`).
- [x] 7.2 Add `BotText` constants for the command name, description, all option names and descriptions, and choice values.
- [x] 7.3 Create `StatsCommandHandler` (`@Component` implementing `SlashCommandHandler`) in `nyt-scorebot-discord/listener/command`.
- [x] 7.4 In the handler, parse and validate options:
   - For `period=custom`, both `from` and `to` are required; reject with an ephemeral error otherwise.
   - For `period != custom`, both `from` and `to` must be absent; reject with an ephemeral error otherwise.
   - Validate `from <= to`; validate `to <= today.minusDays(1)`; validate the range is not entirely before the anchor.
   - If `stats.anchor-date` is unset, reject with an ephemeral "feature disabled" error.
   - If `discord.statsChannelId` is unset, reject with an ephemeral "stats channel not configured" error.
- [x] 7.5 Resolve the effective window: `week` / `month` / `year` per the design table; `all-time` from `max(anchor, earliestDate)` to `today - 1`; `custom` from the validated `from`/`to`.
- [x] 7.6 Determine if the period qualifies as "large": `period ∈ {year, all-time}` or `(custom and (to - from).days > 90)`.
- [x] 7.7 If large, reply ephemerally with the confirmation prompt and two buttons (`stats-confirm:<game>:<period>:<from?>:<to?>` / `stats-cancel:<game>:<period>:<from?>:<to?>`); the actual computation runs in the button handler, not here. After posting the prompt, schedule a 15-second `Mono.delay` that — if not first cancelled — edits the ephemeral message to "Cancelled (timed out)." Track the pending `Disposable` in a `ConcurrentHashMap<String, Disposable>` keyed by interaction token so the button handler can dispose it on click.
- [x] 7.8 If not large, call `event.deferReply()`, run `CrosswordStatsService.compute(...)`, render via `CrosswordStatsReportBuilder`, post the rendered report to the stats channel via `StatsChannelService`, then send a brief ephemeral acknowledgement ("Posted to <#...>").

## 8. Confirmation button handler

- [x] 8.1 Create `StatsConfirmationButtonListener` (`@Component`) that subscribes to `ButtonInteractionEvent` and dispatches on custom IDs starting with `stats-confirm:` and `stats-cancel:`.
- [x] 8.2 On `stats-cancel:*`, edit the original ephemeral message to "Cancelled." and end.
- [x] 8.3 On `stats-confirm:*`, parse the encoded options, edit the original ephemeral message to "Computing report…", run `CrosswordStatsService.compute(...)` + `CrosswordStatsReportBuilder.render(...)`, post the report to the stats channel, and edit the ephemeral message to "Posted to <#...>".
- [x] 8.4 Custom-ID encoding: colon-separated, with empty segments for absent dates (e.g., `stats-confirm:main:year::` for a non-custom request, `stats-confirm:all:custom:2026-01-01:2026-04-30` for a custom request).
- [x] 8.5 On any button click, look up and dispose the pending auto-cancel `Disposable` for this interaction token before processing. If the lookup returns nothing (timer already fired and cleared its own entry), reply ephemerally indicating the prompt has expired and process no further.

## 9. Tests

- [x] 9.1 Unit tests for `CrosswordStatsService.compute(...)`: clean-win attribution, forfeit attribution (only one player submitted), duo/tie/nuke awarding no win, mixed days, played-days-only averaging, best-time tracking with date, participation counts, day-of-week bucketing for Main on a 28+ day window, day-of-week absent on a < 28 day window, anchor clamping (window start raised to anchor), `StatsWindowBeforeAnchorException` thrown when entire window precedes the anchor.
- [x] 9.2 A regression test asserting that the per-day win attribution from `CrosswordStatsService` agrees with the `WinStreakService` outcome on a synthetic dataset that includes at least one of each outcome category (clean win, forfeit, duo, tie, nuke, neither submitted). This is the contract guard for the "stats and streaks tell the same story" decision.
- [x] 9.3 Unit tests for `CrosswordStatsReportBuilder`: per-game block rendering, all-game block rendering, ranking + tie-break (best time), time formatting (sub-hour vs ≥1h, average rounding), Main day-of-week sub-block presence/absence, empty-period placeholder.
- [x] 9.4 Unit tests for `StatsCommandHandler`: each validation rejection (missing from/to on custom, present from/to on non-custom, from > to, to in future, range entirely pre-anchor, anchor unset, stats channel unset), large-period confirmation flow triggered for year / all-time / custom > 90 days, immediate compute + post for small periods.
- [x] 9.5 Unit tests for `StatsConfirmationButtonListener`: cancel path edits to "Cancelled.", confirm path runs the compute and posts to the stats channel, custom-ID parsing for both non-custom and custom shapes, post-timeout click is rejected with an "expired" ephemeral and triggers no compute or post.
- [x] 9.5a Unit test for the 15-second auto-cancel timer in `StatsCommandHandler`: using a `VirtualTimeScheduler` (Reactor's test utility), advance time by 15 seconds and assert the ephemeral message is edited to the "Cancelled (timed out)." text and that the pending entry is removed from the timer map. A separate test asserts a click within 15 seconds disposes the timer (no edit fires when virtual time is then advanced past the 15-second mark).
- [x] 9.6 Unit tests for `WeeklyCrosswordStatsJob`, `MonthlyCrosswordStatsJob`, `YearlyCrosswordStatsJob`: window resolution (last 7 days / last calendar month / last calendar year ending yesterday), no-op when `stats.anchor-date` unset, no-op when `discord.statsChannelId` unset, exception swallowing.
- [x] 9.7 Update `SlashCommandRegistrarTest` (or equivalent) to assert `/stats` is registered with all options and choices.
- [x] 9.8 Verify the existing unit-test command (`mvn test -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'`) passes and that `mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` still meets the 80% JaCoCo threshold.

## 10. Verification

- [x] 10.1 Run `mvn clean package -DskipTests` and confirm a clean build.
- [x] 10.2 Run `mvn test -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` and confirm all tests pass.
- [x] 10.3 Run `mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'` and confirm JaCoCo coverage check passes.
- [ ] 10.4 Manual smoke (post-deploy): invoke `/stats game:main period:week` from a known channel and verify (a) the brief ephemeral acknowledgement appears, (b) the report appears in the stats channel.
- [ ] 10.5 Manual smoke: invoke `/stats game:all period:all-time` and verify the confirmation prompt appears, then the `Run report` button posts the report publicly.
- [ ] 10.6 Manual smoke: invoke `/stats game:main period:custom from:2026-01-01 to:2026-04-30` and verify the report renders and the day-of-week sub-block is present.
