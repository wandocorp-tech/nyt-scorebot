# crossword-stats-reports Specification

## ADDED Requirements

### Requirement: Anchor date gates all stats activity

The system SHALL read a `stats.anchor-date` configuration property of type `LocalDate`. This date defines the earliest date that any stats query SHALL consider. When the property is unset, the entire stats feature SHALL be disabled: scheduled jobs SHALL log a warning and skip, and the `/stats` slash command SHALL reply ephemerally with a "feature disabled" error.

#### Scenario: Anchor unset disables scheduled jobs
- **GIVEN** `stats.anchor-date` is not set
- **WHEN** any of the weekly, monthly, or yearly stats jobs fires
- **THEN** the job logs a warning, posts no message, and exits without throwing

#### Scenario: Anchor unset disables /stats
- **GIVEN** `stats.anchor-date` is not set
- **WHEN** a user invokes `/stats game:main period:week`
- **THEN** the bot replies ephemerally with a message indicating that stats are not configured, and no report is posted

#### Scenario: Window is clamped to the anchor
- **GIVEN** `stats.anchor-date = 2026-05-01` and the user requests `/stats period:custom from:2026-01-01 to:2026-06-01`
- **WHEN** the report is computed
- **THEN** the effective window used for aggregation is `2026-05-01 .. 2026-05-31` (`to` clamped to `today - 1` if today is `2026-06-01`, and `from` raised to the anchor)

#### Scenario: Window entirely before anchor is rejected
- **GIVEN** `stats.anchor-date = 2026-05-01` and the user requests `/stats period:custom from:2026-01-01 to:2026-04-30`
- **WHEN** the bot validates the request
- **THEN** the bot replies ephemerally with an error indicating the requested window is before the configured anchor, and no report is posted

### Requirement: Stats channel is the sole destination for reports

The system SHALL read a `discord.statsChannelId` configuration property. All scheduled and on-demand stats reports SHALL be posted exclusively to this channel. The slash-command reply itself SHALL be a brief ephemeral acknowledgement that the report has been posted, with a Discord channel mention link to the stats channel.

#### Scenario: Stats channel unset disables scheduled jobs
- **GIVEN** `discord.statsChannelId` is not set
- **WHEN** any scheduled stats job fires
- **THEN** the job logs a warning and posts no message

#### Scenario: Stats channel unset rejects /stats
- **GIVEN** `discord.statsChannelId` is not set
- **WHEN** a user invokes `/stats`
- **THEN** the bot replies ephemerally with an error indicating the stats channel is not configured, and no report is posted

#### Scenario: /stats from a non-stats channel still posts to the stats channel
- **GIVEN** `discord.statsChannelId` is configured
- **WHEN** a user invokes `/stats game:mini period:week` from any channel that is not the stats channel
- **THEN** the report is posted publicly to the configured stats channel and the user receives a brief ephemeral acknowledgement that includes a mention of the stats channel

### Requirement: Win attribution agrees with crossword-win-streaks

For each (date, crossword game) in a stats window, the system SHALL determine win attribution using the same `ComparisonOutcome` semantics as the `crossword-win-streaks` capability, plus the same forfeit rule as the win streak midnight rollover. The attribution table SHALL be:

| Situation | Win awarded to |
|---|---|
| `Win` (clean) | Winner |
| `Win` with `duo=true` | No-one |
| `Tie` (Main, both used assistance) | No-one |
| `Nuke` (equal clean times) | No-one |
| Solo submission (only one player has a result for that day/game) | The submitter |
| Neither player submitted | No-one |

#### Scenario: Clean win counts toward the period leaderboard
- **WHEN** Player A submits a faster clean Mini time than Player B on a given day
- **THEN** the period leaderboard for Mini awards +1 win to Player A and 0 to Player B for that day

#### Scenario: Forfeit win counts toward the period leaderboard
- **WHEN** Player A submits the Mini and Player B does not on a given day
- **THEN** the period leaderboard for Mini awards +1 win to Player A for that day

#### Scenario: Duo win is not counted
- **WHEN** Player A wins the Main with `duo=true`
- **THEN** the period leaderboard for Main awards 0 wins to both players for that day

#### Scenario: Tie is not counted
- **WHEN** the Main outcome is `Tie` (both used assistance)
- **THEN** the period leaderboard for Main awards 0 wins to both players for that day

#### Scenario: Nuke is not counted
- **WHEN** the Mini outcome is `Nuke` (equal clean times)
- **THEN** the period leaderboard for Mini awards 0 wins to both players for that day

### Requirement: Period semantics use closed prior windows

The system SHALL resolve each period to a window that ends no later than the day before "today" (in the puzzle timezone), so that no partially-played day is ever included in a report.

| Period | Window resolution |
|---|---|
| `week` | `today.minusDays(7) .. today.minusDays(1)` (slash command); for the scheduled Sunday job, `today.minusDays(7) .. today.minusDays(1)` evaluated when the job runs |
| `month` | `today.minusMonths(1).plusDays(1) .. today.minusDays(1)` (slash command); for the scheduled monthly job, the previous calendar month |
| `year` | `today.minusYears(1).plusDays(1) .. today.minusDays(1)` (slash command); for the scheduled yearly job, the previous calendar year |
| `all-time` | `max(anchor-date, earliest-scoreboard-date) .. today.minusDays(1)` |
| `custom` | `from .. to` inclusive, with `from` raised to the anchor if necessary |

#### Scenario: Slash-command week excludes today
- **GIVEN** today is `2026-05-08`
- **WHEN** a user invokes `/stats period:week`
- **THEN** the report covers `2026-05-01 .. 2026-05-07`

#### Scenario: Scheduled monthly job covers the previous calendar month
- **GIVEN** the monthly job runs on `2026-06-01`
- **THEN** the posted report covers `2026-05-01 .. 2026-05-31`

#### Scenario: All-time uses the anchor as a floor
- **GIVEN** `stats.anchor-date = 2026-05-01` and the earliest scoreboard date is `2026-04-15`
- **WHEN** the report is computed for `period:all-time`
- **THEN** the effective lower bound is `2026-05-01`, not `2026-04-15`

### Requirement: Custom range validation

For `period:custom`, the system SHALL require both `from` and `to` options, both formatted as `YYYY-MM-DD`. The system SHALL reject the request ephemerally without posting any report when:

- Either `from` or `to` is missing (when `period=custom`).
- Either `from` or `to` is provided (when `period != custom`).
- `from > to`.
- `to > today.minusDays(1)`.
- The entire range is before `stats.anchor-date`.

A single-day range (`from == to`) SHALL be accepted and produce a one-day report.

#### Scenario: Missing from on custom is rejected
- **WHEN** a user invokes `/stats period:custom to:2026-04-30`
- **THEN** the bot replies ephemerally with a validation error and posts nothing

#### Scenario: from after to is rejected
- **WHEN** a user invokes `/stats period:custom from:2026-04-30 to:2026-04-01`
- **THEN** the bot replies ephemerally with a validation error and posts nothing

#### Scenario: to in the future is rejected
- **GIVEN** today is `2026-05-08`
- **WHEN** a user invokes `/stats period:custom from:2026-04-01 to:2026-05-09`
- **THEN** the bot replies ephemerally with a validation error and posts nothing

#### Scenario: Single-day range is accepted
- **WHEN** a user invokes `/stats period:custom from:2026-04-15 to:2026-04-15`
- **THEN** the bot computes a report for that one day and posts it (or proceeds through the confirmation flow if applicable)

#### Scenario: from/to provided on a non-custom period is rejected
- **WHEN** a user invokes `/stats period:week from:2026-04-01 to:2026-04-07`
- **THEN** the bot replies ephemerally with a validation error and posts nothing

### Requirement: Confirmation prompt for large windows

The system SHALL present an ephemeral confirmation prompt with `Run report` and `Cancel` buttons whenever the requested period qualifies as large. A period qualifies as large when it is `year`, `all-time`, or a `custom` range whose duration exceeds 90 days. The prompt text SHALL signal that the window is wider without using the word "expensive".

#### Scenario: Year period triggers confirmation
- **WHEN** a user invokes `/stats period:year`
- **THEN** the bot replies ephemerally with a confirmation prompt and two buttons; no report is computed or posted until the user clicks `Run report`

#### Scenario: All-time period triggers confirmation
- **WHEN** a user invokes `/stats period:all-time`
- **THEN** the bot replies ephemerally with a confirmation prompt and two buttons

#### Scenario: Custom range over 90 days triggers confirmation
- **WHEN** a user invokes `/stats period:custom from:2026-01-01 to:2026-04-30`
- **THEN** the bot replies ephemerally with a confirmation prompt and two buttons

#### Scenario: Custom range up to 90 days does not trigger confirmation
- **WHEN** a user invokes `/stats period:custom from:2026-04-01 to:2026-06-30`
- **THEN** the bot proceeds directly to compute and post the report, with no confirmation step

#### Scenario: Cancel button posts nothing
- **GIVEN** a user has been shown the confirmation prompt
- **WHEN** the user clicks `Cancel`
- **THEN** the ephemeral message is edited to indicate cancellation and no report is posted

#### Scenario: Run report button posts publicly
- **GIVEN** a user has been shown the confirmation prompt
- **WHEN** the user clicks `Run report`
- **THEN** the ephemeral message is updated to indicate the report is being computed, the report is computed and posted publicly to the stats channel, and the ephemeral message is then updated to confirm the report has been posted

### Requirement: Confirmation prompt auto-cancels after 15 seconds

When the system presents the large-window confirmation prompt, it SHALL also start a 15-second timer. If neither `Run report` nor `Cancel` is clicked before the timer elapses, the system SHALL edit the ephemeral message to indicate that the prompt has timed out, and SHALL ignore any subsequent click on the now-stale buttons. If a button is clicked before the timer elapses, the timer SHALL be cancelled and the button click handled normally.

#### Scenario: Prompt auto-cancels after 15 seconds of inactivity
- **GIVEN** a user has been shown the confirmation prompt and has not clicked either button
- **WHEN** 15 seconds elapse
- **THEN** the ephemeral message is edited to indicate the prompt has timed out, and no report is computed or posted

#### Scenario: Click before timeout cancels the timer
- **GIVEN** a user has been shown the confirmation prompt
- **WHEN** the user clicks `Run report` or `Cancel` within 15 seconds
- **THEN** the timer is cancelled, the click is handled normally, and no auto-cancel edit occurs

#### Scenario: Click after timeout is ignored
- **GIVEN** the confirmation prompt has already auto-cancelled after 15 seconds
- **WHEN** the user subsequently clicks `Run report` or `Cancel` on the now-stale prompt
- **THEN** the system does not compute or post a report and does not edit the ephemeral message further

### Requirement: Scheduled reports

The system SHALL post crossword stats reports on three fixed cadences in the puzzle timezone (`${discord.timezone:Europe/London}`):

- **Weekly** — Sunday at 21:00, covering the previous 7 days (`today - 7 .. today - 1`).
- **Monthly** — the 1st of each month at 09:00, covering the previous calendar month.
- **Yearly** — 1 January at 09:00, covering the previous calendar year.

Each scheduled job SHALL render an "all games" report (Mini, Midi, and Main). Each job SHALL skip silently with a logged warning when either `stats.anchor-date` or `discord.statsChannelId` is unset, and SHALL log and swallow any exception thrown by the computation or posting steps so that one failed run does not crash the bot or affect future scheduled runs.

#### Scenario: Weekly job posts on Sunday at 21:00
- **GIVEN** the system clock reaches Sunday 21:00 in the configured timezone and both anchor and stats channel are configured
- **WHEN** the weekly job fires
- **THEN** an "all games" report covering the previous 7 days is posted to the stats channel

#### Scenario: Monthly job posts on the 1st at 09:00
- **GIVEN** the system clock reaches the 1st of the month at 09:00 in the configured timezone
- **WHEN** the monthly job fires
- **THEN** an "all games" report covering the previous calendar month is posted to the stats channel

#### Scenario: Yearly job posts on 1 January at 09:00
- **GIVEN** the system clock reaches 1 January at 09:00 in the configured timezone
- **WHEN** the yearly job fires
- **THEN** an "all games" report covering the previous calendar year is posted to the stats channel

#### Scenario: Empty window posts an empty-period placeholder
- **GIVEN** no scoreboard data exists for any date in the window
- **WHEN** any scheduled job fires
- **THEN** the posted report contains the empty-period placeholder text rather than empty leaderboards

### Requirement: Report content and ranking

For each requested game, the rendered report SHALL include:

- A leaderboard sorted by win count descending, with ties broken by best solve time ascending.
- For each player on the leaderboard: their rank (medal emoji for 1st/2nd/3rd, plain numbers thereafter), their display name, their win count, their average solve time across played days only, and their best solve time and the date of that best.

Times SHALL be formatted as `m:ss` for sub-hour values and `h:mm:ss` for values of one hour or more. Average times SHALL be rounded to the nearest second.

#### Scenario: Leaderboard ordering by wins
- **GIVEN** Player A has 5 wins and Player B has 3 wins for a game in the window
- **WHEN** the report is rendered
- **THEN** Player A is ranked 1st and Player B is ranked 2nd

#### Scenario: Tie-break by best time
- **GIVEN** Player A and Player B both have 4 wins, with best times 0:32 and 0:28 respectively
- **WHEN** the report is rendered
- **THEN** Player B is ranked above Player A

#### Scenario: Average uses played days only
- **GIVEN** in a 7-day window Player A submitted on 4 days with times totalling 8:00 and Player B submitted on 7 days with times totalling 21:00
- **WHEN** the report is rendered
- **THEN** Player A's average is shown as `2:00` (8:00 / 4) and Player B's average is shown as `3:00` (21:00 / 7)

#### Scenario: Time format scales with magnitude
- **WHEN** a Main average is `1:23:45`
- **THEN** the report renders it as `1:23:45`, not `83:45` or `5025`

### Requirement: Main day-of-week breakdown for month-or-larger windows

When the requested game includes Main and the resolved window length is at least 28 days, the rendered report SHALL include a dedicated day-of-week breakdown sub-block beneath (or as part of) the Main section. The sub-block SHALL contain seven rows, one per `DayOfWeek` from Monday through Sunday, with one column per player. Each cell SHALL show the player's average Main solve time on that weekday across the window followed by the count of played days for that weekday in parentheses (e.g., `4:12 (4)`). Days where the player did not play SHALL be excluded from their average and count for that weekday.

#### Scenario: Day-of-week block present on a 28-day window
- **GIVEN** the resolved window is exactly 28 days
- **WHEN** the Main report is rendered
- **THEN** the day-of-week sub-block is included with seven rows

#### Scenario: Day-of-week block absent on a 27-day window
- **GIVEN** the resolved window is 27 days
- **WHEN** the Main report is rendered
- **THEN** no day-of-week sub-block is included

#### Scenario: Day-of-week absent on weekly reports
- **WHEN** the weekly scheduled job runs
- **THEN** the posted report does not include a Main day-of-week sub-block

#### Scenario: Played-day count is shown next to each weekday average
- **GIVEN** Player A played the Main on 4 of the 5 Tuesdays in a window
- **WHEN** the Main day-of-week sub-block is rendered
- **THEN** the Tuesday cell for Player A shows the average time followed by `(4)`

### Requirement: Empty-period placeholder

When no scoreboard data exists for any date in the resolved window, the rendered report SHALL contain a placeholder message indicating that no crossword results were found, rather than empty leaderboards or game sections.

#### Scenario: No data in window
- **GIVEN** the resolved window contains no scoreboard rows
- **WHEN** the report is rendered
- **THEN** the output contains the placeholder text and does not contain any per-game leaderboard rows
