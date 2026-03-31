## 1. Strands Spangram Tracking

- [x] 1.1 Add `spangram position` field to `StrandsResult` — new `Integer spangram position` field with getter, update constructor to accept it
- [x] 1.2 Update `StrandsParser` to compute spangram position (1-based index of 🟡 in the flattened emoji sequence) and pass it to the `StrandsResult` constructor
- [x] 1.3 Add `@AttributeOverride` for `strands_spangram_position` column to the `Scoreboard` entity's `StrandsResult` embed
- [x] 1.4 Update `ParserTest` strands test cases to assert the spangram position is correctly parsed

## 2. Scoreboard Framework

- [x] 2.1 Create the `GameComparisonScoreboard` interface in `service/scoreboard/` with methods: `gameType()`, `header(...)`, `scoreLabel(...)`, `emojiGridRows(...)`, `determineOutcome(...)`, `gridLeadingSpaces()`, `gridGap()`, `emojisPerRow()`
- [x] 2.2 Create the `PlayerColumn` value object holding player name, score label, and emoji grid rows
- [x] 2.3 Create `ComparisonOutcome` — a sealed type representing Tie, Win (winner name, differential), WaitingFor (missing player name)
- [x] 2.4 Create `ScoreboardRenderer` — the shared layout engine that takes a `GameComparisonScoreboard`, two `Scoreboard` entities, and configured player names/order, and renders the 35-char-wide Discord code block using the shared layout rules (header, separator, name row, grid, result message)

## 3. Game-Specific Scoreboards

- [x] 3.1 Implement `WordleScoreboard` — header (`Wordle #N`), score (1–6 or X), 5-emoji rows with 4 leading spaces / 5-space gap, winner by fewer guesses
- [x] 3.2 Implement `ConnectionsScoreboard` — header (`Connections #N`), score (0–N or X), 4-emoji rows with 6 leading spaces / 5-space gap, winner by fewer mistakes
- [x] 3.3 Implement `StrandsScoreboard` — header (`Strands #N - "tagline"`), score (0–N hints), up to 4-emoji rows with 6 leading spaces / 5-space gap (2 extra spaces per missing emoji for alignment), winner by fewer hints then earlier spangram

## 4. Integration

- [x] 4.1 Add `discord.resultsChannelId` to `DiscordChannelProperties` configuration (new property, separate from `discord.statusChannelId`)
- [x] 4.2 Add scoreboard result message constants to `BotText` (tie, win with differential, win without differential, waiting)
- [x] 4.3 Add an in-memory `Map<String, Snowflake>` (keyed by game type, e.g. `"WORDLE"`) in a new `ResultsChannelService` (or in `StatusChannelService`) to track the Discord message ID of each posted scoreboard
- [x] 4.4 Create `ResultsChannelService` (or extend `StatusChannelService`) to: (a) check if both players are "done" (`areBothPlayersDone()`); (b) for each game, call `ScoreboardRenderer`; (c) post a new message per game scoreboard to the results channel and store its message ID
- [x] 4.5 Update `StatusChannelService` to handle late submissions: when a result is saved, check if a scoreboard message already exists for that game in the results channel — if so, delete it and re-post a new completed scoreboard
- [x] 4.6 Wire up the game scoreboard beans — register `WordleScoreboard`, `ConnectionsScoreboard`, `StrandsScoreboard` as Spring `@Component`s and inject the list into `ScoreboardRenderer`
- [x] 4.7 Add helper method `areBothPlayersDone(LocalDate date)` in `ScoreboardService`, checking `finished` flag and game submission counts for both players

## 5. Tests

- [x] 5.1 Unit tests for `WordleScoreboard` — win (both complete), tie (same guesses), tie (both X), win (one X), single submission, no submissions
- [x] 5.2 Unit tests for `ConnectionsScoreboard` — win (both complete), tie (same mistakes), tie (both X), win (one X), single submission
- [x] 5.3 Unit tests for `StrandsScoreboard` — win by hints, win by spangram position, tie (same hints + same spangram), single submission
- [x] 5.4 Unit tests for `ScoreboardRenderer` — shared layout rendering, column ordering by grid length, column ordering by config for ties
- [x] 5.5 Unit tests for `ResultsChannelService` scoreboard posting — both players done triggers post; single-player layout when one used `/finished` without submitting
- [x] 5.6 Unit tests for `ResultsChannelService` scoreboard replacement — late submission after single-player scoreboard is posted deletes old message and reposts completed scoreboard
- [x] 5.7 Run `mvn verify -Dtest='!com.wandocorp.nytscorebot.SmokeTest'` and confirm ≥80% coverage passes
