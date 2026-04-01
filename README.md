# NYT Scorebot

A Discord bot that captures and persists New York Times (NYT) daily puzzle results from Discord chat, storing them in an H2 database for easy tracking and comparison.

## Overview

The bot monitors one or more Discord channels and recognizes when users post their results for NYT games:
- **Wordle** — daily 5-letter word puzzle
- **Connections** — daily 4-group categorization puzzle
- **Strands** — daily semantic word puzzle
- **Crossword** — daily, mini, and midi crossword variants

Results are validated against the expected puzzle numbers for the current date (in GMT), deduplicated to prevent duplicate submissions, and persisted to an H2 database.

Once both players have finished for the day (either by submitting all games or using the `/finished` slash command), the bot posts head-to-head comparison scoreboards to a dedicated results channel — one per game. If a player used `/finished` without submitting every game, incomplete single-player scoreboards are posted and automatically replaced with full two-player boards if the other player later submits.

## Architecture & Design Decisions

### 1. **Puzzle Number Validation**

**Problem:** Users might post outdated puzzle results or the bot might process duplicate messages.

**Solution:** 
- Introduced `PuzzleCalendar` service that calculates expected puzzle numbers for Wordle, Connections, and Strands based on known epoch dates and GMT timezone.
- Each numbered game (Wordle, Connections, Strands) validates that the posted puzzle number matches today's expected number.
- Crosswords embed their own date in the result text, so they can be submitted for past/future dates (catching up or looking ahead).

**Key Decisions:**
- Use **GMT/BST timezone** (Europe/London) for puzzle validation, not the NYT's New York timezone. This ensures the submission window is a full calendar day (midnight to midnight) in a single timezone.
- Crosswords are stored against their own embedded date, not validated against today — allows users to submit results for any date they've solved.
- Wordle #0 = 2021-06-19; Connections #1 = 2023-06-12; Strands #1 = 2024-03-04 (anchors for calculating daily puzzle numbers).

### 2. **Deduplication**

**Problem:** Discord events can fire multiple times; users might manually post the same result twice.

**Solution:**
- Each user+date combination maps to a single `Scoreboard` entity.
- Before persisting a result, check if that game type is already present on today's scoreboard.
- Duplicate detection checks `rawContent != null` (not just existence), accounting for Hibernate 6 always instantiating empty `@Embeddable` objects.

**Result Outcomes:**
- `SAVED` — successfully persisted
- `WRONG_PUZZLE_NUMBER` — puzzle number doesn't match today's expected
- `WRONG_DATE` — (deprecated in current version)
- `ALREADY_SUBMITTED` — duplicate submission for this game type today

### 3. **Deferred Rejection Replies**

**Problem:** When a result is rejected, users should know why.

**Solution:**
- `MessageListener` catches all Discord events and filters early (channel monitored? configured user?).
- `ScoreboardService.saveResult()` returns a `SaveOutcome` enum indicating why a result was accepted or rejected.
- `replyForOutcome()` sends a Discord message to the channel explaining rejections (e.g., "⚠️ That doesn't look like today's puzzle number.").
- Rejection replies are **non-blocking** — use `.subscribe()` (fire-and-forget) rather than `.block()`, since the message is informational and async propagation is acceptable.

### 4. **Multi-Channel & Multi-User Support**

**Problem:** Different Discord servers / channels may want to track different players.

**Solution:**
- `DiscordChannelProperties` (from `application.yml`) maps channel IDs → player names and authorized user IDs.
- `MessageListener` maintains two maps: `channelPersonMap` (channel → player name) and `channelUserIdMap` (channel → authorized Discord user ID).
- Each (user, date) pair maps to one `Scoreboard` row; users in different channels are separate User entities.
- Early filtering: if the channel isn't monitored or the user isn't authorized, the message is silently ignored.

### 5. **Embeddable Game Results**

**Problem:** Multiple game types need to coexist on a single Scoreboard without polluting the entity with nullable fields.

**Solution:**
- Used JPA `@Embeddable` for each game result type (`WordleResult`, `ConnectionsResult`, `StrandsResult`, `CrosswordResult`).
- `Scoreboard` embeds one of each and uses attribute overrides to map to distinct DB columns (e.g., `wordle_puzzle_number`, `connections_puzzle_number`).
- Null check on `rawContent` to determine if a result slot is occupied (handles Hibernate 6 behavior of always instantiating embeddables).

### 6. **Parser Strategy**

**Problem:** Different game types have different text formats; need flexible, ordered parsing.

**Solution:**
- `GameResultParser` chains multiple `GameParser` implementations, each annotated with `@Order`.
- Each parser is responsible for:
  - Pattern matching its game type
  - Extracting structured data (puzzle number, attempts, mistakes, time, comments)
  - Returning `Optional.empty()` if the content doesn't match
- Parsers run in order; first match wins. Early return on successful parse.

**Parsing Order:**
1. WordleParser
2. ConnectionsParser
3. StrandsParser
4. CrosswordParser (lowest priority due to generic formats)

**Design Rationale:** Ordering ensures more specific parsers run first, avoiding false positives from generic crossword-like text.

### 7. **Slash Command: `/finished`**

**Purpose:** Allow users to explicitly mark their daily scoreboard as complete.

**Behavior:**
- Global slash command `/finished` registered on bot startup.
- When invoked, sets the `finished` flag to `true` on the invoking user's Scoreboard for today's date.
- Replies with an ephemeral message (visible only to the user) indicating the outcome:
  - ✅ Success: "Your scoreboard for today has been marked as finished!"
  - Already finished, no scoreboard today, or not a tracked user — informational replies.
- Also triggers a results channel refresh (see §8 below).

**Implementation:**
- `SlashCommandRegistrar` handles global command registration via Discord's ApplicationService.
- `SlashCommandListener` subscribes to `ChatInputInteractionEvent` and dispatches to the service layer.
- `ScoreboardService.markFinished()` performs the database operation and returns `MarkFinishedOutcome` enum.

**Use Case:**
- A user who has submitted some but not all games can signal they're done for the day, allowing comparison scoreboards to be published immediately rather than waiting for all six games.

### 8. **Game Comparison Scoreboards**

**Purpose:** Post head-to-head comparison scoreboards once both players are done for the day.

**Posting Lifecycle:**
1. After every result submission or `/finished` command, `ResultsChannelService.refresh()` is called.
2. It checks `areBothPlayersFinishedToday()` — both players must have `finished=true` (set either by submitting all 6 games or via `/finished`).
3. For each emoji-grid game (Wordle, Connections, Strands), a scoreboard is rendered and posted as a separate Discord message to the **results channel** (`discord.resultsChannelId`).
4. Games where one player used `/finished` without submitting produce **single-player scoreboards** (showing only the present player's data and a "⏳ [Other] hasn't submitted" message).
5. If a single-player scoreboard is already posted and the other player later submits, the existing message is **deleted and replaced** with the completed two-player board.
6. Games where neither player submitted produce no scoreboard.

**Layout (33-char fixed-width Discord code block):**
```
 Wordle #1234
 
-----------------------------------
     William - 6  |  Conor - 4
-----------------------------------
    ⬛⬛⬛🟨⬛     ⬛⬛⬛🟨⬛
    ⬛⬛⬛⬛🟨     ⬛⬛⬛⬛🟨
    🟨🟨🟩⬛⬛     🟨🟨🟩⬛⬛
    🟩🟩🟩🟩⬛     🟩🟩🟩🟩🟩
    ⬛🟨🟩🟨⬛
    🟩🟩🟩🟩⬛
-----------------------------------
 🏆 Conor wins! (-2)
-----------------------------------
```

**Column ordering:** The player with more emoji grid rows goes left. Ties follow configured player order.

**Winner determination:**
- Wordle: fewer guesses; X (failed) loses to any completion.
- Connections: fewer mistakes; X loses to any completion.
- Strands: fewer hints used; tiebreaker removed — hints-only.

**Extensibility:** Adding a new game scoreboard requires only implementing `GameComparisonScoreboard` — no changes to `ScoreboardRenderer` or `ResultsChannelService`.

**Note:** Strands no longer stores a `spangramPosition`; only `hintsUsed` is persisted and used for scoreboard winner determination.

**In-memory message tracking:** `ResultsChannelService` holds a `Map<gameType, Snowflake>` of posted message IDs. Cleared on restart; a subsequent submission will re-trigger posting.

## Testing Approach

### Test Coverage Goal: 80% (instructions + branches)

All tests must compile and pass with JaCoCo enabled:
```bash
mvn test                          # Run all tests including EndToEndTest
mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'  # Run unit tests + JaCoCo check
```

### Test Layers

#### 1. **Unit Tests** (160 tests, ~30 seconds)

**ParserTest** — GameResultParser chain & individual parser logic
- Wordle parsing (attempts, hard mode, completion, comments)
- Connections parsing (puzzle number, mistakes, solved groups)
- Strands parsing (hints, puzzle number)
- Crossword parsing (daily/mini/midi, date extraction, comments)
- Edge cases: non-matching content, missing dates, empty comments

**ScoreboardServiceTest** — Validation, deduplication, entity persistence
- Puzzle number validation (correct ✓, wrong ✗)
- Crossword date handling (accepts past dates)
- Duplicate detection (same game type on same date)
- Entity creation paths (new User, new Scoreboard)
- `areBothPlayersFinishedToday()` behaviour
- Mocks repositories; uses concrete `FixedPuzzleCalendar` stub for deterministic dates

**MessageListenerTest** — Discord event filtering & message reply logic
- Channel monitoring (monitored vs. ignored)
- User authorization (configured vs. unauthorized)
- Message processing (parse → validate → persist → reply)
- Event filtering chain (3 tests covering all permutations)
- Reply outcomes for all 4 SaveOutcome types
- Uses inline mock maker for final Discord4J classes (`Message`, `MessageChannel`)

**Scoreboard rendering tests** — Game comparison scoreboard logic
- `WordleScoreboardTest` — win, tie (same guesses), tie (both X), win by completion, score labels, emoji grid extraction
- `ConnectionsScoreboardTest` — same structure for Connections
- `StrandsScoreboardTest` — win by hints, tie
- `ScoreboardRendererTest` — shared layout, column ordering by row count, column ordering by config order for ties, single-player layout, no-result no-op

**ResultsChannelServiceTest** — Results channel posting logic
- No-op when `resultsChannelId` is null or blank
- No-op when both players are not finished
- Posts per-game messages when both done
- Deletes and reposts on late submission

**PuzzleCalendarTest** — Puzzle number calculation

**DiscordChannelPropertiesTest, StringListConverterTest, GameResultParserTest** — Configuration, converters, misc

#### 2. **End-to-End Test** (~50 seconds)

**EndToEndTest** — Real Discord connection, live database, full message flow
- Requires configured Discord bot, test channels, H2 database
- Validates full round-trip: message → parse → validate → persist → query

### Running Tests

```bash
# Unit tests only (excludes EndToEndTest, ~30 seconds)
mvn test -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'

# All tests including end-to-end test (requires Discord & DB, ~80 seconds)
mvn test

# Verify + JaCoCo check (≥80% coverage threshold)
mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'
```

### JaCoCo Configuration

**Threshold:** 80% instruction and branch coverage (BUNDLE level)

**Exclusions:** Data-only classes, repositories, configuration, enums, application entry point:
- `com/wandocorp/nytscorebot/model/**`
- `com/wandocorp/nytscorebot/entity/User.class`
- `com/wandocorp/nytscorebot/entity/Scoreboard.class`
- `com/wandocorp/nytscorebot/repository/**`
- `com/wandocorp/nytscorebot/service/SaveOutcome.class`
- `com/wandocorp/nytscorebot/NytScorebotApplication.class`
- `com/wandocorp/nytscorebot/DiscordConfig.class`

**Bytecode Instrumentation:** Uses inline mock maker to mock final Discord4J classes with ByteBuddy experimental flag (`-Dnet.bytebuddy.experimental=true`).

## Configuration

### Discord Bot Setup

1. Create a Discord application at https://discord.com/developers/applications
2. Enable the Message Content Intent (under Bot → Privileged Gateway Intents)
3. Copy the bot token

### Application Configuration

Create `application.yml`:
```yaml
discord:
  token: "your-bot-token"
  # statusChannelId: "345678901"    # Optional: channel for the live submission status table
  # resultsChannelId: "456789012"   # Optional: channel for head-to-head comparison scoreboards
  channels:
    - id: "123456789"
      name: "Player One"
      userId: "987654321"    # Authorized Discord user ID
    - id: "234567890"
      name: "Player Two"
      userId: "876543210"
```

Both `statusChannelId` and `resultsChannelId` are optional. Omit either to disable that channel's output.

### Database

H2 in-memory database with Spring Data JPA auto-configuration. Schema is auto-generated on startup (`ddl-auto=update`) to add new nullable columns when necessary.

For persistence across restarts, configure `spring.datasource.url`:
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/scorebot
```

## Key Components

### `MessageListener` (Component)
- Subscribes to Discord `MessageCreateEvent` stream
- Filters by monitored channels & authorized users
- Delegates to `GameResultParser` and `ScoreboardService`
- On `SAVED`: triggers `StatusChannelService.refresh()` (status table) and `ResultsChannelService.refresh()` (comparison scoreboards)
- Sends rejection replies via `replyForOutcome()`

### `SlashCommandListener` (Component)
- Handles `/finished` slash command
- Calls `ScoreboardService.markFinished()` and sends an ephemeral reply
- On success: triggers both `StatusChannelService.refresh()` and `ResultsChannelService.refresh()`

### `GameResultParser` (Component)
- Chains multiple `GameParser` implementations
- Each parser checks if content matches its game type
- Returns first successful parse or `Optional.empty()`

### `ScoreboardService` (Service)
- Validates puzzle numbers against `PuzzleCalendar`
- Checks for duplicates
- Persists results and creates User/Scoreboard entities as needed
- Auto-sets `finished=true` when all 6 games are present
- Returns `SaveOutcome` / `MarkFinishedOutcome` enums
- `areBothPlayersFinishedToday()` — used by `ResultsChannelService`

### `StatusChannelService` (Service)
- Posts/refreshes the submission status table in the configured status channel
- Tracks the previous message ID to delete-and-replace on each refresh

### `ResultsChannelService` (Service)
- Called after every result submission or `/finished`
- Posts head-to-head comparison scoreboard messages to the results channel
- Only activates when both players have `finished=true` for today
- Tracks posted message IDs; deletes and reposts when a late submission upgrades a single-player board to two-player

### `ScoreboardRenderer` (Component)
- Shared layout engine for 33-char-wide comparison scoreboards
- Handles two-player, single-player, and no-op cases
- Injects all `GameComparisonScoreboard` implementations; `renderAll()` iterates them

### `GameComparisonScoreboard` (Interface)
- Implemented by `WordleScoreboard`, `ConnectionsScoreboard`, `StrandsScoreboard`
- Each implementation provides: `hasResult()`, `header()`, `scoreLabel()`, `emojiGridRows()`, `determineOutcome()`, spacing constants
- New games can be added by implementing this interface — no other changes required

### `PuzzleCalendar` (Service)
- Calculates expected puzzle numbers for today (GMT timezone)
- Methods: `expectedWordle()`, `expectedConnections()`, `expectedStrands()`
- Also provides package-private methods taking an explicit date for testing

### Data Entities
- **User** — (channel_id, name, discord_user_id); one per monitored channel
- **Scoreboard** — (user_id, date); one per user+date combination, embeds all game results, includes `finished` flag

## Design Trade-offs

| Decision | Rationale |
|----------|-----------|
| GMT timezone for puzzles | Single, predictable submission window (not dependent on user location) |
| Crossword dates embedded in results | Allows catching up on past puzzles or previewing future ones |
| Early channel/user filtering in MessageListener | Avoids unnecessary parsing & DB queries for ignored messages |
| Inline mock maker for Discord4J | Enables unit testing without full bot integration; trade-off is increased build complexity (ByteBuddy experimental flag) |
| Non-blocking rejection replies | Fire-and-forget async messages; user doesn't wait for ACK, but may not see reply immediately |
| Puzzle number validation only (no date validation for numbered games) | Simpler logic; Wordle/Connections/Strands already published fresh daily, so puzzle number is sufficient |
| Scoreboards posted to separate results channel | Keeps game results visually distinct from the submission status table |
| In-memory message ID tracking | Sufficient for single-instance bot; restarts are rare, and the next submission re-triggers posting |
| `spangramPosition` (removed) | No longer stored; Strands uses hints-only for comparison |
| Strategy pattern for game scoreboard logic | Adding new game scoreboards requires one new class, zero changes to the shared rendering pipeline |

## Build & Deploy

```bash
# Build JAR
mvn clean package -DskipTests

# Run
java -jar target/nyt-scorebot-*.jar
```

Requires:
- Java 17+
- Spring Boot 3.x
- H2 database
- Discord bot token with Message Content intent

## CI/CD (GitHub Actions)

The project uses GitHub Actions for automated build, test, and deployment.

### Workflows

| Workflow | File | Trigger | Purpose |
|---|---|---|---|
| **Build** | `build.yml` | `workflow_call`, `workflow_dispatch` | Compile, run unit tests, enforce JaCoCo coverage, upload JAR artifact |
| **Test (E2E)** | `test.yml` | `workflow_call`, `workflow_dispatch` | Run `EndToEndTest` against live Discord |
| **Deploy** | `deploy.yml` | `workflow_call`, `workflow_dispatch` | Copy JAR to Raspberry Pi via SCP, restart systemd service |
| **Pipeline** | `pipeline.yml` | Push to `main`, `workflow_dispatch` | Orchestrate build → test → deploy in sequence |
| **Release** | `release.yml` | `workflow_dispatch` (with version input) | Build JAR and create a GitHub Release with it attached |

Each of build, test, and deploy can be run independently from the GitHub Actions UI, or as part of the pipeline.

### Required Repository Secrets

| Secret | Purpose |
|---|---|
| `DISCORD_TOKEN` | Discord bot token (used by E2E test and the application) |
| `PI_SSH_KEY` | SSH private key for authenticating to the Raspberry Pi |
| `PI_HOST` | Hostname or IP address of the Pi |
| `PI_USER` | SSH username on the Pi |
| `PI_SSH_PORT` | SSH port (defaults to 22 if not set) |
| `PI_DEPLOY_PATH` | Remote directory where the JAR is placed (e.g., `/opt/scorebot/`) |
| `PI_SERVICE_NAME` | systemd service name to restart (e.g., `scorebot`) |

### Raspberry Pi Prerequisites

- Java 17+ installed
- SSH server running and accessible from GitHub Actions runners
- A systemd service unit configured to run the application JAR (e.g., `/etc/systemd/system/scorebot.service`)
- The deploy user must have passwordless `sudo` access for `systemctl restart`

## Overview

The bot monitors one or more Discord channels and recognizes when users post their results for NYT games:
- **Wordle** — daily 5-letter word puzzle
- **Connections** — daily 4-group categorization puzzle
- **Strands** — daily semantic word puzzle
- **Crossword** — daily, mini, and midi crossword variants

Users can also invoke the `/finished` slash command to mark their daily scoreboard as complete, signaling that they've finished submitting results for the day.

Results are validated against the expected puzzle numbers for the current date (in GMT), deduplicated to prevent duplicate submissions, and persisted to an H2 database for leaderboards and analysis.

## Architecture & Design Decisions

### 1. **Puzzle Number Validation**

**Problem:** Users might post outdated puzzle results or the bot might process duplicate messages.

**Solution:** 
- Introduced `PuzzleCalendar` service that calculates expected puzzle numbers for Wordle, Connections, and Strands based on known epoch dates and GMT timezone.
- Each numbered game (Wordle, Connections, Strands) validates that the posted puzzle number matches today's expected number.
- Crosswords embed their own date in the result text, so they can be submitted for past/future dates (catching up or looking ahead).

**Key Decisions:**
- Use **GMT/BST timezone** (Europe/London) for puzzle validation, not the NYT's New York timezone. This ensures the submission window is a full calendar day (midnight to midnight) in a single timezone.
- Crosswords are stored against their own embedded date, not validated against today — allows users to submit results for any date they've solved.
- Wordle #0 = 2021-06-19; Connections #1 = 2023-06-12; Strands #1 = 2024-03-04 (anchors for calculating daily puzzle numbers).

### 2. **Deduplication**

**Problem:** Discord events can fire multiple times; users might manually post the same result twice.

**Solution:**
- Each user+date combination maps to a single `Scoreboard` entity.
- Before persisting a result, check if that game type is already present on today's scoreboard.
- Duplicate detection checks `rawContent != null` (not just existence), accounting for Hibernate 6 always instantiating empty `@Embeddable` objects.

**Result Outcomes:**
- `SAVED` — successfully persisted
- `WRONG_PUZZLE_NUMBER` — puzzle number doesn't match today's expected
- `WRONG_DATE` — (deprecated in current version)
- `ALREADY_SUBMITTED` — duplicate submission for this game type today

### 3. **Deferred Rejection Replies**

**Problem:** When a result is rejected, users should know why.

**Solution:**
- `MessageListener` catches all Discord events and filters early (channel monitored? configured user?).
- `ScoreboardService.saveResult()` returns a `SaveOutcome` enum indicating why a result was accepted or rejected.
- `replyForOutcome()` sends a Discord message to the channel explaining rejections (e.g., "⚠️ That doesn't look like today's puzzle number.").
- Rejection replies are **non-blocking** — use `.subscribe()` (fire-and-forget) rather than `.block()`, since the message is informational and async propagation is acceptable.

### 4. **Multi-Channel & Multi-User Support**

**Problem:** Different Discord servers / channels may want to track different players.

**Solution:**
- `DiscordChannelProperties` (from `application.yml`) maps channel IDs → player names and authorized user IDs.
- `MessageListener` maintains two maps: `channelPersonMap` (channel → player name) and `channelUserIdMap` (channel → authorized Discord user ID).
- Each (user, date) pair maps to one `Scoreboard` row; users in different channels are separate User entities.
- Early filtering: if the channel isn't monitored or the user isn't authorized, the message is silently ignored.

### 5. **Embeddable Game Results**

**Problem:** Multiple game types need to coexist on a single Scoreboard without polluting the entity with nullable fields.

**Solution:**
- Used JPA `@Embeddable` for each game result type (`WordleResult`, `ConnectionsResult`, `StrandsResult`, `CrosswordResult`).
- `Scoreboard` embeds one of each and uses attribute overrides to map to distinct DB columns (e.g., `wordle_puzzle_number`, `connections_puzzle_number`).
- Null check on `rawContent` to determine if a result slot is occupied (handles Hibernate 6 behavior of always instantiating embeddables).

### 6. **Parser Strategy**

**Problem:** Different game types have different text formats; need flexible, ordered parsing.

**Solution:**
- `GameResultParser` chains multiple `GameParser` implementations, each annotated with `@Order`.
- Each parser is responsible for:
  - Pattern matching its game type
  - Extracting structured data (puzzle number, attempts, mistakes, time, comments)
  - Returning `Optional.empty()` if the content doesn't match
- Parsers run in order; first match wins. Early return on successful parse.

**Parsing Order:**
1. WordleParser
2. ConnectionsParser
3. StrandsParser
4. CrosswordParser (lowest priority due to generic formats)

**Design Rationale:** Ordering ensures more specific parsers run first, avoiding false positives from generic crossword-like text.

## Testing Approach

### Test Coverage Goal: 80% (instructions + branches)

All tests must compile and pass with JaCoCo enabled:
```bash
mvn test                          # Run all tests including EndToEndTest
mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'  # Run unit tests + JaCoCo check
```

### Test Layers

#### 1. **Unit Tests** (70 tests, ~30 seconds)

**ParserTest** — GameResultParser chain & individual parser logic
- Wordle parsing (attempts, hard mode, completion, comments)
- Connections parsing (puzzle number, mistakes, solved groups)
- Strands parsing (hints, puzzle number)
- Crossword parsing (daily/mini/midi, date extraction, comments)
- Edge cases: non-matching content, missing dates, empty comments

**ScoreboardServiceTest** — Validation, deduplication, entity persistence
- Puzzle number validation (correct ✓, wrong ✗)
- Crossword date handling (accepts past dates)
- Duplicate detection (same game type on same date)
- Entity creation paths (new User, new Scoreboard)
- Mocks repositories; uses concrete `FixedPuzzleCalendar` stub for deterministic dates

**MessageListenerTest** — Discord event filtering & message reply logic
- Channel monitoring (monitored vs. ignored)
- User authorization (configured vs. unauthorized)
- Message processing (parse → validate → persist → reply)
- Event filtering chain (3 tests covering all permutations)
- Reply outcomes for all 4 SaveOutcome types
- Uses inline mock maker for final Discord4J classes (`Message`, `MessageChannel`)

**PuzzleCalendarTest** — Puzzle number calculation
- Anchor dates for each game
- No-arg methods return today's expected numbers
- Fixed-date methods for deterministic testing

**DiscordChannelPropertiesTest, StringListConverterTest, GameResultParserTest**
- Configuration parsing
- Entity type conversion
- Miscellaneous components

#### 2. **End-to-End Test** (~50 seconds)

**EndToEndTest** — Real Discord connection, live database, full message flow
- Requires configured Discord bot, test channels, H2 database
- Validates full round-trip: message → parse → validate → persist → query

### Running Tests

```bash
# Unit tests only (excludes EndToEndTest, ~30 seconds)
mvn test -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'

# All tests including end-to-end test (requires Discord & DB, ~80 seconds)
mvn test

# Verify + JaCoCo check (≥80% coverage threshold)
mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'
```

### JaCoCo Configuration

**Threshold:** 80% instruction and branch coverage (BUNDLE level)

**Exclusions:** Data-only classes, repositories, configuration, enums, application entry point:
- `com/wandocorp/nytscorebot/model/**`
- `com/wandocorp/nytscorebot/entity/User.class`
- `com/wandocorp/nytscorebot/entity/Scoreboard.class`
- `com/wandocorp/nytscorebot/repository/**`
- `com/wandocorp/nytscorebot/service/SaveOutcome.class`
- `com/wandocorp/nytscorebot/NytScorebotApplication.class`
- `com/wandocorp/nytscorebot/DiscordConfig.class`

**Bytecode Instrumentation:** Uses inline mock maker to mock final Discord4J classes with ByteBuddy experimental flag (`-Dnet.bytebuddy.experimental=true`).

## Configuration

### Discord Bot Setup

1. Create a Discord application at https://discord.com/developers/applications
2. Enable the Message Content Intent (under Bot → Privileged Gateway Intents)
3. Copy the bot token

### Application Configuration

Create `application.yml`:
```yaml
discord:
  token: "your-bot-token"
  # statusChannelId: "345678901"   # Optional: channel ID for the live status board
  channels:
    - id: "123456789"
      name: "Player One"
      userId: "987654321"    # Authorized Discord user ID
    - id: "234567890"
      name: "Player Two"
      userId: "876543210"
```

### Database

H2 in-memory database with Spring Data JPA auto-configuration. Schema is auto-generated on startup.

For persistence across restarts, configure `spring.datasource.url`:
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/gameresults
```

## Key Components

### `MessageListener` (Component)
- Subscribes to Discord `MessageCreateEvent` stream
- Filters by monitored channels & authorized users
- Delegates to `GameResultParser` and `ScoreboardService`
- Sends rejection replies via `replyForOutcome()`

### `GameResultParser` (Component)
- Chains multiple `GameParser` implementations
- Each parser checks if content matches its game type
- Returns first successful parse or `Optional.empty()`

### `ScoreboardService` (Service)
- Validates puzzle numbers against `PuzzleCalendar`
- Checks for duplicates
- Persists results and creates User/Scoreboard entities as needed
- Returns `SaveOutcome` enum indicating success or reason for rejection

### `PuzzleCalendar` (Service)
- Calculates expected puzzle numbers for today (GMT timezone)
- Methods: `expectedWordle()`, `expectedConnections()`, `expectedStrands()`
- Also provides package-private methods taking an explicit date for testing

### Data Entities
- **User** — (channel_id, name, discord_user_id); one per monitored channel
- **Scoreboard** — (user_id, date); one per user+date combination, embeds all game results, includes `complete` flag

### 7. **Slash Command: `/finished`**

**Purpose:** Allow users to explicitly mark their daily scoreboard as complete.

**Behavior:**
- Global slash command `/finished` registered on bot startup
- When invoked, sets the `complete` flag to `true` on the invoking user's Scoreboard for today's date
- Replies with an ephemeral message (visible only to the user) indicating the outcome:
  - ✅ Success: "Your scoreboard for today has been marked as complete!"
  - ℹ️ Already complete: "Your scoreboard was already marked complete for today."
  - ℹ️ No scoreboard: "You haven't submitted any results for today yet."
  - ℹ️ Not tracked: "You are not a tracked user in this bot."

**Implementation:**
- `SlashCommandRegistrar` handles global command registration via Discord's ApplicationService
- `SlashCommandListener` subscribes to `ChatInputInteractionEvent` and dispatches to the service layer
- `ScoreboardService.markComplete()` performs the database operation and returns `MarkCompleteOutcome` enum
- Reorganized into `com.wandocorp.nytscorebot.listener` package for separation of concerns

**Use Case:**
- Users can signal completion before the end of the submission window, enabling the bot to process rankings with incomplete result sets when needed

## Design Trade-offs

| Decision | Rationale |
|----------|-----------|
| GMT timezone for puzzles | Single, predictable submission window (not dependent on user location) |
| Crossword dates embedded in results | Allows catching up on past puzzles or previewing future ones |
| Early channel/user filtering in MessageListener | Avoids unnecessary parsing & DB queries for ignored messages |
| Inline mock maker for Discord4J | Enables unit testing without full bot integration; trade-off is increased build complexity (ByteBuddy experimental flag) |
| Non-blocking rejection replies | Fire-and-forget async messages; user doesn't wait for ACK, but may not see reply immediately |
| Puzzle number validation only (no date validation for numbered games) | Simpler logic; Wordle/Connections/Strands already published fresh daily, so puzzle number is sufficient |

## Future Improvements

- **Leaderboards** — Query Scoreboard by user or date range to generate rankings
- **Stats API** — REST endpoint to fetch results without parsing Discord chat
- **Timezone flexibility** — Allow each channel to specify its preferred timezone
- **Admin commands** — Discord slash commands for manual data correction/deletion
- **Message retention policy** — Auto-archive old Scoreboards after N days

## Build & Deploy

```bash
# Build JAR
mvn clean package -DskipTests

# Run
java -jar target/nyt-scorebot-*.jar
```

Requires:
- Java 17+
- Spring Boot 3.x
- H2 database
- Discord bot token with Message Content intent
