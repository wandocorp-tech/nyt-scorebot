# Copilot Instructions — NYT Scorebot

A Spring Boot Discord bot that parses and persists New York Times daily puzzle results (Wordle, Connections, Strands, Crossword) posted in Discord channels.

## Build & Test

```bash
# Build JAR
mvn clean package -DskipTests

# Unit tests only (~30s) — use this for day-to-day development
mvn test -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'

# Run a single test class
mvn test -Dtest=ParserTest

# Run a single test method
mvn test -Dtest=ParserTest#wordleResultIsParsed

# Full suite including end-to-end test (~80s, requires live Discord connection and DB)
mvn test

# Verify with JaCoCo coverage check (≥80% instruction + branch)
mvn verify -Dtest='!com.wandocorp.nytscorebot.EndToEndTest'

# Run SonarCloud analysis locally (requires SONAR_TOKEN env var)
mvn sonar:sonar -Dsonar.organization=wandocorp-tech -Dsonar.projectKey=nyt-scorebot
```

The Surefire plugin requires ByteBuddy flags (already configured in `pom.xml`) to support Mockito's inline mock maker for final Discord4J classes.

## Code Quality (SonarCloud)

Every push to `main` triggers a SonarCloud analysis as part of the build pipeline. View results:
- **Dashboard:** https://sonarcloud.io/organizations/wandocorp-tech/projects
- **Project:** https://sonarcloud.io/project/overview?id=wandocorp-tech_nyt-scorebot

Current status: 47 issues (2 critical, 6 major, 39 minor)
- **Critical:** Wildcard generics in `MessageListener` (lines 79, 118)
- **Major:** Test code issues (unused vars, sleep in tests), regex performance in `CrosswordParser`
- **Minor:** Mostly test utilities and Lombok annotations

## Architecture

**Request flow:**
1. Discord `MessageCreateEvent` → `MessageListener` filters by channel + authorized user ID
2. `GameResultParser` tries each `GameParser` impl in `@Order` sequence (1–4), returns first match
3. `ScoreboardService` validates puzzle number against `PuzzleCalendar`, checks for duplicates, persists
4. `StatusChannelService` rebuilds the live leaderboard in the configured status channel
5. `/finished` slash command → `SlashCommandListener` → `ScoreboardService.markFinished()` → ephemeral reply

**Package responsibilities:**

| Package | Role |
|---|---|
| `listener` | Discord event subscribers; message routing, filtering, replies |
| `parser` | One `GameParser` per game type; `GameResultParser` orchestrates |
| `service` | Business logic: validation, deduplication, persistence, leaderboard |
| `entity` | JPA entities: `User` (1 per channel slot) and `Scoreboard` (1 per user per day) |
| `model` | `GameResult` subclasses — one per game type, all `@Embeddable` |
| `config` | `DiscordChannelProperties` binds YAML channel list |

**`Scoreboard` entity** embeds all six game results (`WordleResult`, `ConnectionsResult`, `StrandsResult`, and three `CrosswordResult` variants) as JPA `@Embedded` fields with `@AttributeOverrides` for distinct column names. One row per user per day; a unique constraint enforces this.

## Key Conventions

**Parsers use `@Order` — order matters.** More specific regex patterns come first (Wordle=1, Connections=2, Crossword=3, Strands=4) so the chain short-circuits correctly.

**Outcome enums for explicit failure reasons.** `ScoreboardService` returns `SaveOutcome` / `MarkFinishedOutcome` enums rather than throwing exceptions. `MessageListener` maps these to Discord reply strings.

**Puzzle number validation is calendar-based.** `PuzzleCalendar` calculates the expected puzzle number for today using fixed anchor dates (Wordle #0 = 2021-06-19, etc.) in the GMT/BST timezone. Tests use `FixedPuzzleCalendar` to fix the date.

**Discord4J is reactive (Project Reactor).** Event handlers return `Mono<Void>`. Rejection replies use `.subscribe()` (fire-and-forget). The status channel refresh also runs non-blocking.

**`BotText` is the single source of UI strings** — all emoji, labels, and reply messages live there. Don't inline display strings elsewhere.

**JaCoCo excludes boilerplate from coverage:** `model/*`, `entity/User`, `entity/Scoreboard`, `repository/*`, `SaveOutcome`, `NytScorebotApplication`, `DiscordConfig`. New business logic added to `service/` or `listener/` must maintain the 80% threshold.

## Running the Application

```bash
# Build and run
mvn clean package -DskipTests
java -jar target/nyt-scorebot-*.jar
```

Requires Java 17+, a Discord bot token, and the **Message Content Intent** enabled in the Discord Developer Portal (Bot → Privileged Gateway Intents).

## Configuration

`application.properties` (or environment overrides):

```properties
discord.token=${DISCORD_TOKEN}           # Required env var
discord.channels[0].id=<channel-id>
discord.channels[0].name=PlayerName      # Display name used in leaderboard
discord.channels[0].user-id=<user-id>   # Only this Discord user may submit in this channel
discord.statusChannelId=<channel-id>    # Optional; omit to disable live leaderboard

spring.datasource.url=jdbc:h2:file:./data/scorebot
spring.jpa.hibernate.ddl-auto=update
```

The Discord bot also requires **Message Content Intent** enabled in the Discord Developer Portal (Bot → Privileged Gateway Intents).

## Testing Patterns

- **Unit tests** mock Discord4J classes (Mockito inline mock maker) and inject `FixedPuzzleCalendar` for deterministic dates.
- **`EndToEndTest`** is a full end-to-end test that requires a live Discord connection and is excluded from normal `mvn test` runs via the `-Dtest='!...EndToEndTest'` flag.
- Parser tests follow a 3-sample-per-parser pattern: a passing case, an edge case, and a failure case.
- Use AssertJ assertions (`assertThat(...)`), not JUnit `assertEquals`.

## Commit Message Conventions

All commits SHOULD use a conventional-commit prefix so that AI-generated release notes can categorise and filter changes. Format: `<type>: <subject>` (e.g., `feat: add /streak slash command for tracking game streaks`).

Allowed prefixes:

| Prefix | Meaning |
|---|---|
| `feat` | User-visible new feature |
| `fix` | User-visible bug fix |
| `refactor` | Internal restructure with no behaviour change |
| `chore` | Housekeeping (dependency bumps, formatting, etc.) |
| `ci` | Pipeline / GitHub Actions changes |
| `test` | Test-only changes |
| `docs` | Documentation only |
| `build` | Build, packaging, or Maven changes |

The CI release-notes generator filters out `chore`, `ci`, `test`, and `build` commits unless they have user-visible impact, so prefixing accurately keeps the Discord release announcements concise. The convention is guidance only — non-conventional commits are not blocked.
