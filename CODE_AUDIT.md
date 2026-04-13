# Code Audit — NYT Scorebot

**Date:** 2026-04-12
**Methodology:** Full manual review of all source, test, configuration, and CI/CD files against Clean Code principles (Robert C. Martin), the 12-Factor App methodology, and existing SonarCloud analysis.

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 6     |
| High     | 13    |
| Medium   | 18    |
| Low      | 8     |

---

## 1 — Architecture & Design

### 1.1 ● CRITICAL — Scoreboard entity is a god class (wide table with 50+ columns)

**Files:** `nyt-scorebot-database/.../entity/Scoreboard.java:36-119`

The `Scoreboard` entity embeds six game results via `@Embedded` with 62 individual `@AttributeOverride` annotations. This produces a single database table with 50+ columns — a textbook Wide Table anti-pattern.

Three nearly identical `CrosswordResult` blocks (Mini, Midi, Main) each repeat 10 `@AttributeOverride` entries. Adding a new game requires editing this entity, the service, the listener, and every `instanceof` chain (see §1.2).

**Principle:** SRP, OCP, database normalisation.

---

### 1.2 ● CRITICAL — Pervasive `instanceof` chains instead of polymorphism

**Files:**
- `ScoreboardService.java:97-112` — `validate()`
- `ScoreboardService.java:114-132` — `isAlreadySubmitted()`
- `ScoreboardService.java:134-148` — `applyResult()`
- `ScoreboardService.java:194-201` — `allGamesPresent()`
- `MessageListener.java:130-142` — `gameLabel()`
- `StreakService.java:89-94` — `resolveGameType()`
- `StreakService.java:96-100` — `isSuccess()`

Seven separate `if/else instanceof` chains all discriminate on the same `GameResult` subtype. Adding a new game type requires touching every single one. This is the classic "switch smell" from Clean Code ch. 3 — the fix is polymorphism or a visitor/strategy.

**Principle:** OCP, polymorphism over conditionals.

---

### 1.3 ● HIGH — System is hardcoded for exactly two players

**Files:**
- `ScoreboardService.java:33` — `scoreboards.size() >= 2`
- `ResultsChannelService.java:56-65` — `channels.get(0)` / `channels.get(1)`
- `ScoreboardRenderer.java:63-71` — left/right two-player layout
- `StatusMessageBuilder.java` — loop over player list (flexible, but callers assume 2)

The two-player assumption is spread across service, discord, and rendering layers with no central constant or configuration. Extending to N players would require changes in many files.

**Principle:** OCP, configuration over convention.

---

### 1.4 ● HIGH — `CrosswordResult` violates SRP (dual-purpose model)

**File:** `nyt-scorebot-domain/.../model/CrosswordResult.java:13-46`

One class serves three crossword variants. The `duo`, `lookups`, and `checkUsed` fields (lines 22-24) only apply to the Main crossword; they are meaningless nulls for Mini/Midi. The `toString()` method branches on type (line 39). This mixes two distinct responsibilities into one model.

**Principle:** SRP — separate `MainCrosswordResult` from `TimedCrosswordResult`.

---

### 1.5 ● MEDIUM — `DiscordConfig` lives in the root package

**File:** `nyt-scorebot-discord/.../DiscordConfig.java`

Every other class in the discord module is under a subpackage (`config`, `listener`, `discord`). `DiscordConfig` sits alone at the root `com.wandocorp.nytscorebot` package, breaking the module's own organisational conventions.

**Principle:** Package cohesion.

---

### 1.6 ● MEDIUM — `BotText` is a constants bag, not an object

**File:** `nyt-scorebot-domain/.../BotText.java:1-99`

~60 `public static final String` fields in a utility class with no behaviour. Constants are not grouped semantically (emoji, messages, commands, labels are interleaved). Consider organising into nested classes or an enum-based approach.

**Principle:** Clean Code ch. 17 — "Constants versus Enums".

---

## 2 — 12-Factor App Violations

### 2.1 ● CRITICAL — `spring.jpa.hibernate.ddl-auto=update` in production config

**File:** `nyt-scorebot-app/src/main/resources/application.properties:23`

`ddl-auto=update` lets Hibernate silently alter the production schema. This is dangerous and non-reproducible. 12-Factor says build, release, run should be strictly separated. Schema changes should use a migration tool (Flyway / Liquibase) and be versioned in source control.

**Principle:** 12-Factor V (Build, release, run), 12-Factor X (Dev/prod parity).

---

### 2.2 ● HIGH — File-based H2 database as the sole data store

**File:** `nyt-scorebot-app/src/main/resources/application.properties:17`

`jdbc:h2:file:./data/scorebot` ties the application to a local filesystem path. If the process restarts on a different host (or the data directory is lost), all state is gone. H2 is suitable for dev/test, but production should use an attached backing service (Postgres, MySQL).

**Principle:** 12-Factor IV (Backing services as attached resources).

---

### 2.3 ● HIGH — Environment-specific channel IDs committed to source

**File:** `nyt-scorebot-app/src/main/resources/application.properties:5-14`

Discord channel IDs (`1358111788146622709`, etc.), player names, and user IDs are hardcoded in the checked-in properties file. Config that varies between deployments should live in the environment, not in code.

**Principle:** 12-Factor III (Store config in the environment).

---

### 2.4 ● MEDIUM — No health check or readiness endpoint

The application has no Spring Boot Actuator dependency and exposes no `/health` or `/ready` endpoint. The deploy workflow restarts the systemd service with no way to verify the new process is healthy.

**Principle:** 12-Factor IX (Disposability), 12-Factor XII (Admin processes).

---

### 2.5 ● MEDIUM — No structured logging

Logging uses plain `Slf4j` string interpolation (`log.info("Saved {} result for {}", ...)`). There is no JSON/structured log output. 12-Factor says logs should be unbuffered event streams; structured formats make them parseable by log aggregators.

**Principle:** 12-Factor XI (Logs as event streams).

---

### 2.6 ● MEDIUM — No graceful shutdown handling

The application blocks on `client.onDisconnect().block()` (line 22, `NytScorebotApplication.java`) but has no shutdown hook to gracefully disconnect from Discord, flush pending messages, or close the database cleanly.

**Principle:** 12-Factor IX (Disposability — fast startup, graceful shutdown).

---

### 2.7 ● LOW — Hardcoded puzzle epoch dates

**File:** `PuzzleCalendar.java:22-28`

Wordle, Connections, and Strands epoch dates are hardcoded constants. If NYT changes numbering or a new game is added, a code change + redeploy is required. These could be externalised to config.

**Principle:** 12-Factor III (Config).

---

## 3 — Reactive & Concurrency Issues

### 3.1 ● CRITICAL — `.block()` in `@Bean` and `@PostConstruct` methods

**Files:**
- `DiscordConfig.java:28` — `.login().block()`
- `SlashCommandRegistrar.java:23` — `.getApplicationId().block()`

Blocking calls during Spring context initialisation defeat the purpose of the reactive stack and can deadlock under constrained thread pools. The Discord4J docs recommend non-blocking bean creation.

**Principle:** Don't block in reactive pipelines.

---

### 3.2 ● CRITICAL — Fire-and-forget `.subscribe()` with no error handling

**Files:**
- `StatusChannelService.java:37`
- `ResultsChannelService.java:121, 125`
- `MessageListener.java:119`
- `SlashCommandListener.java:54`
- `StatusMessageListener.java:37`
- `SlashCommandRegistrar.java:82`

At least 8 `.subscribe()` calls have no `onError` consumer. Errors in these reactive chains are silently swallowed — a failed Discord API call, a serialisation error, or a network timeout would produce zero log output.

**Principle:** Clean Code ch. 7 — "Don't Ignore Caught Exceptions" (reactive equivalent).

---

### 3.3 ● HIGH — `Mono<?>` wildcard generics lose type safety

**File:** `MessageListener.java:79, 122`

`processMessage()` and `replyForOutcome()` return `Mono<?>`. The wildcard erases the actual type, preventing callers from chaining typed operators. These should return `Mono<Void>`.

**Sonar:** This is one of the 2 existing **Critical** SonarCloud issues (wildcard generics in `MessageListener` lines 79, 118).

---

### 3.4 ● HIGH — Race condition in `ResultsChannelService`

**File:** `ResultsChannelService.java:34, 42-44, 52`

`volatile LocalDate lastRefreshDate` is checked (`lastRefreshDate.equals(today)`) and then set (`lastRefreshDate = puzzleCalendar.today()`) without synchronisation. Two concurrent calls to `refresh()` can both pass the check before either sets the flag, causing duplicate posts. `volatile` alone does not make compound check-then-act operations atomic.

**Principle:** Thread safety — use `AtomicReference.compareAndSet()` or synchronise.

---

### 3.5 ● MEDIUM — Potential NPE in `isFromConfiguredUser()`

**File:** `MessageListener.java:74-76`

```java
String configuredUserId = channelUserIdMap.get(channelId);
return configuredUserId.equals(authorId);  // NPE if channelId not in map
```

`channelUserIdMap.get()` can return `null` if the channel is not configured, causing a `NullPointerException`. The guard `isChannelMonitored()` on line 111 should prevent this, but the method is `public` and unprotected when called directly.

---

## 4 — Fat Classes & Methods

### 4.1 ● HIGH — `ScoreboardService.saveResult()` — 42 lines, 6 responsibilities

**File:** `ScoreboardService.java:54-95`

This method validates puzzle numbers, resolves dates, creates-or-fetches a User, creates-or-fetches a Scoreboard, checks for duplicates, applies the result, updates streaks, and auto-sets the finished flag. It should be decomposed into focused private methods.

**Principle:** Clean Code ch. 3 — "Functions should do one thing."

---

### 4.2 ● HIGH — `ScoreboardRenderer.render()` — 35 lines, complex branching

**File:** `ScoreboardRenderer.java:41-75`

Checks result existence, determines sort order, branches between single-player and two-player rendering paths. Extract `determineLayout()` and delegate.

---

### 4.3 ● MEDIUM — `SlashCommandListener` — 209 lines, 8 dependencies

**File:** `SlashCommandListener.java:1-209`

This class handles five different slash commands (`/finished`, `/duo`, `/lookups`, `/check`, `/streak`), each with distinct logic. It has 8 injected dependencies. Consider splitting into per-command handlers using a command pattern or Spring's `@EventListener`.

**Principle:** SRP — one class, one reason to change.

---

### 4.4 ● MEDIUM — `SlashCommandRegistrar.registerCommands()` — monolithic command builder

**File:** `SlashCommandRegistrar.java:22-84`

63-line method that builds and registers all five slash commands in one block. Each command definition is independent and could be extracted to a data-driven registration loop.

---

### 4.5 ● MEDIUM — `ResultsChannelService` — `refresh()` and `refreshGame()` are ~80% duplicated

**File:** `ResultsChannelService.java:47-113`

Both methods fetch scoreboards, resolve player names, build a `byName` map, build streaks, and post messages. The only difference is whether all games or a single game are rendered. Extract a shared `prepareContext()` method.

**Principle:** DRY.

---

## 5 — Naming & Clarity

### 5.1 ● HIGH — `User.userId` field vs constructor param `discordUserId`

**File:** `User.java:27-34`

The JPA field is named `userId` but the constructor parameter is `discordUserId`. This creates ambiguity: `user.getUserId()` could be mistaken for the entity's primary key (`id`). Rename the field to `discordUserId` for clarity.

---

### 5.2 ● MEDIUM — `leftSb` / `rightSb` swap logic is confusing

**File:** `ScoreboardRenderer.java:63-71`

Variables named `leftSb`/`rightSb` suggest spatial position, but the swap is based on emoji row count (the player with *more* rows goes left). Without a comment, a reader would assume left=player1. Use `longerGridSb`/`shorterGridSb` or add a comment.

---

### 5.3 ● MEDIUM — `refreshMainCrossword()` sends misleading context message

**File:** `SlashCommandListener.java:145-154`

When a user toggles `/duo` or `/check`, the status channel message reads "X is done for today" (`STATUS_CONTEXT_PLAYER_FINISHED`). This is semantically wrong — the user set a flag, they didn't finish. Use a distinct context message.

---

### 5.4 ● MEDIUM — Parser `@Order` values don't match documentation

**Documented:** Wordle=1, Connections=2, Strands=3, Crossword=4
**Actual:** Wordle=1, Connections=2, **Crossword=3**, **Strands=4**

The copilot instructions and README say Strands is `@Order(3)` and Crossword is `@Order(4)`, but the code is reversed. The patterns are distinct enough that it works, but the docs are misleading.

---

### 5.5 ● LOW — `MAX_LINE_WIDTH = 33` is unexplained

**File:** `BotText.java:5`

No comment or named derivation for why the line width is exactly 33. Is it a Discord monospace constraint? A two-player name limit? Document the origin.

---

### 5.6 ● LOW — `Streak.gameType` is a raw `String` instead of an enum

**File:** `Streak.java:29`, `StreakRepository.java:12`

Game type is stored as a free-form `String`. Any typo (e.g., `"wordle"` vs `"Wordle"`) would silently create a duplicate record. A `GameType` enum with `@Enumerated(EnumType.STRING)` would add compile-time safety.

---

## 6 — Code Smells & Anti-Patterns

### 6.1 ● HIGH — Boxed primitives (`Integer`, `Boolean`) where primitives suffice

**Files:**
- `WordleResult.java:10-13`
- `ConnectionsResult.java:14-16`
- `StrandsResult.java:10-11`
- `CrosswordResult.java:18`

Fields like `puzzleNumber`, `attempts`, `completed`, `hardMode`, and `hintsUsed` are declared as `Integer`/`Boolean` but initialised from primitive constructor params. This causes unnecessary autoboxing, potential NPEs on unbox, and is misleading — these fields are never semantically null for a valid result.

**Note:** JPA `@Embeddable` requires boxed types for nullable embedded fields (to distinguish "not submitted" from "submitted with value 0"). This is a design trade-off forced by the wide-table embedding strategy (§1.1). Normalising to separate tables would eliminate the need.

---

### 6.2 ● MEDIUM — `CrosswordParser.extractDate()` returns `null` without `Optional`

**File:** `CrosswordParser.java:72-92`

Returns `null` on line 91 when no date is found, but callers don't check for null. The result is passed through `build()` into `CrosswordResult.date`, which may be null. Use `Optional<LocalDate>` for the return type.

---

### 6.3 ● MEDIUM — `ConnectionsParser.lastIndexOf(line)` is unreliable

**File:** `ConnectionsParser.java:48`

```java
lastRowEnd = Math.max(lastRowEnd, content.lastIndexOf(line) + line.length());
```

If two emoji rows are identical (e.g., two rows of 🟩🟩🟩🟩), `lastIndexOf` returns the position of the *last* occurrence, not the current one. This could miscalculate `lastRowEnd` and corrupt comment extraction.

---

### 6.4 ● MEDIUM — `GameResultParser` uses redundant Optional chain

**File:** `GameResultParser.java:24-28`

```java
.filter(Optional::isPresent)
.findFirst()
.flatMap(o -> o);
```

The idiomatic form is:
```java
.flatMap(p -> p.parse(content, discordAuthor).stream())
.findFirst();
```

---

### 6.5 ● MEDIUM — `StrandsParser` has a formatting defect (missing newline)

**File:** `StrandsParser.java:42-43`

```java
return Optional.of(new StrandsResult(...));
}    private String extractComment(...) {
```

The closing brace and next method declaration are on the same line. While syntactically valid, this is clearly a merge artifact or formatting oversight.

---

### 6.6 ● MEDIUM — `new java.util.HashMap<>()` — fully qualified import

**File:** `ResultsChannelService.java:138`

`new java.util.HashMap<>()` instead of just `new HashMap<>()`. This usually indicates an import conflict or oversight. Either add the import or switch to `Map.of()` / `Map.copyOf()`.

---

### 6.7 ● LOW — Magic numbers in `SlashCommandRegistrar`

**File:** `SlashCommandRegistrar.java:42, 58, 70`

`.type(4)` and `.type(3)` are bare Discord API option type integers. Use named constants: `ApplicationCommandOptionType.INTEGER.getValue()` or define local constants.

---

### 6.8 ● LOW — `PLAYER_COL_WIDTH = 15` is a magic number

**File:** `ScoreboardRenderer.java:18`

The column width for player names is hardcoded to 15, not derived from configuration or actual name lengths. If a player has a longer name, the layout breaks.

---

## 7 — Test Quality

### 7.1 ● HIGH — E2E test relies on `Thread.sleep()` for synchronisation

**File:** `EndToEndTest.java:72, 132-142, 171-181`

The test uses 15+ explicit `Thread.sleep()` calls (1-5 seconds each) to wait for async Discord operations. This is inherently flaky — too short and the test fails on slow CI; too long and the suite is needlessly slow. Use Awaitility or polling assertions instead.

**Sonar:** Related to the existing **Major** SonarCloud issue about sleep in tests.

---

### 7.2 ● MEDIUM — E2E test has hardcoded channel IDs as constants *and* in properties

**File:** `EndToEndTest.java:50-51` + `application-e2e.properties:12-21`

Channel IDs appear in both the test class and the properties file. If one changes, the other silently drifts. Single source of truth: inject via `@Value` or `@Autowired DiscordChannelProperties`.

---

### 7.3 ● MEDIUM — `StringListConverter` tests don't cover delimiter collision

**File:** `nyt-scorebot-domain/.../entity/StringListConverterTest.java`

The converter uses comma as delimiter (`String.join(",", list)` / `value.split(",", -1)`). No test verifies behaviour when list items contain commas. A round-trip with `List.of("a,b", "c")` would silently corrupt data to `["a", "b", "c"]`.

---

### 7.4 ● LOW — Duplicate mock setup in `StatusChannelServiceTest`

**File:** `StatusChannelServiceTest.java:50-51`

`when(scoreboardService.getTodayScoreboards()).thenReturn(List.of())` is set up twice — dead code.

---

## 8 — Build, CI/CD & Configuration

### 8.1 ● HIGH — Pipeline triggers on *every* push to *every* branch

**File:** `.github/workflows/pipeline.yml:3-4`

```yaml
on:
  push:
```

No branch filter means every push to every feature branch runs the full pipeline (build → E2E → deploy → release). The deploy and release jobs gate on `refs/heads/main`, but the E2E test (which requires a live Discord connection) runs on every branch, wasting CI minutes and potentially conflicting.

---

### 8.2 ● MEDIUM — Version `1.0-SNAPSHOT` is hardcoded in multiple places

**Files:**
- `pom.xml:16` — `<version>1.0-SNAPSHOT</version>`
- `build.yml:45` — `nyt-scorebot-app-1.0-SNAPSHOT.jar`
- `deploy.yml:57, 75` — `nyt-scorebot-app-1.0-SNAPSHOT.jar`
- `release.yml:43` — `nyt-scorebot-app-1.0-SNAPSHOT.jar`

The version string is repeated across POM and CI workflows. A version bump requires editing 5+ files. Use `${project.version}` or `mvn help:evaluate` in CI.

---

### 8.3 ● MEDIUM — Release workflow builds the JAR a second time

**File:** `release.yml:35`

The release workflow runs `mvn clean package -DskipTests` even though the pipeline already built and uploaded the JAR in the build step. This rebuilds from source with no guarantee it produces the same artifact. Download the artifact from the build step instead.

---

### 8.4 ● LOW — JaCoCo branch coverage threshold differs between modules

**Files:**
- Root `pom.xml:153` — branch minimum `0.80` (80%)
- `nyt-scorebot-discord/pom.xml:70` — branch minimum `0.60` (60%)

The discord module has a lower branch coverage threshold (60% vs 80%) with no documented justification. This creates an inconsistent quality bar.

---

### 8.5 ● LOW — `sonar.projectKey` mismatch between POM and CI

**Files:**
- Root `pom.xml:33` — `<sonar.projectKey>nyt-scorebot</sonar.projectKey>`
- `build.yml:36` — `-Dsonar.projectKey=wandocorp-tech_nyt-scorebot`

The project key in the POM doesn't match the one in the CI workflow. The CI value wins at runtime, but the POM value is misleading.

---

## 9 — Existing SonarCloud Issues

Per the latest SonarCloud analysis: **47 issues** (2 Critical, 6 Major, 39 Minor).

### Critical (2)
| Issue | File | Lines |
|---|---|---|
| Wildcard generic `Mono<?>` — use bounded type | `MessageListener.java` | 79, 122 |

*Covered in §3.3.*

### Major (6)
| Issue | File |
|---|---|
| Unused variable in test code | Various test files |
| `Thread.sleep()` used in tests | `EndToEndTest.java` |
| Regex performance risk in `CrosswordParser` | `CrosswordParser.java` |
| Test code quality (missing assertions, dead setup) | Various test files |

*Sleep issue covered in §7.1. Regex issue: the `.+?` patterns in `MINI`, `MIDI`, and `DAILY` regexes (CrosswordParser:18-29) use lazy quantifiers that can cause polynomial backtracking on adversarial input — a minor ReDoS risk.*

### Minor (39)
Mostly Lombok annotation style, test utility conventions, and unused imports. Not individually itemised here — refer to [SonarCloud dashboard](https://sonarcloud.io/project/overview?id=wandocorp-tech_nyt-scorebot).

---

## 10 — Quick Reference: Principles Cited

| Principle | Findings |
|---|---|
| **SRP** (Single Responsibility) | §1.1, §1.2, §1.4, §4.1, §4.3 |
| **OCP** (Open/Closed) | §1.2, §1.3 |
| **DRY** (Don't Repeat Yourself) | §1.2, §4.5, §7.2 |
| **12-Factor III** (Config in env) | §2.3, §2.7 |
| **12-Factor IV** (Backing services) | §2.2 |
| **12-Factor V** (Build/release/run) | §2.1 |
| **12-Factor IX** (Disposability) | §2.4, §2.6 |
| **12-Factor XI** (Logs) | §2.5 |
| **Clean Code ch. 3** (Small functions) | §4.1, §4.2, §4.4 |
| **Clean Code ch. 7** (Error handling) | §3.2 |
| **Thread safety** | §3.4, §3.5 |

---

## 11 — Comments (fill-in)

Please add one short action or decision per finding by editing the placeholder below. Use the finding number as the key (e.g., `1.1 — Normalize Scoreboard: <your note>`).

1.1 — fix it

1.2 — Use polymorphism and refactor the chains (Recommended)

1.3 — Make two-player explicit and document it. this is only ever intended for 2 players max

1.4 — Split into MainCrosswordResult and TimedCrosswordResult (Recommended)

1.5 — Move to config package (Recommended)

1.6 — Group constants semantically or use enums (Recommended)

2.1 — Switch to Flyway (Recommended)

2.2 — Use managed Postgres/MySQL in production (Recommended)

2.3 — Move to environment variables / external config (Recommended)

2.4 — Add Spring Boot Actuator and health/readiness endpoints (Recommended)

2.5 — Introduce structured JSON logging (Recommended)

2.6 — Add proper shutdown hooks and graceful disconnect (Recommended)

2.7 — Keep hardcoded

3.1 — Remove blocking calls and refactor beans to be non-blocking (Recommended)

3.2 — Add onError consumers and centralised Reactor error handling (Recommended)

3.3 — Change return types to Mono<Void> or specific typed Monos (Recommended)

3.4 — Use AtomicReference.compareAndSet or synchronized block (Recommended)

3.5 — Null-safe compare (Objects.equals) or require map.containsKey guard (Recommended)

4.1 — Refactor into smaller private methods (Recommended)

4.2 — Extract determineLayout and delegate (Recommended)

4.3 — Split into per-command handlers (Recommended)

4.4 — Extract each command registration into separate methods or a data-driven loop (Recommended)

4.5 — Extract shared prepareContext() helper (Recommended)

5.1 — Rename field to discordUserId (Recommended)

5.2 — Rename to longerGridSb/shorterGridSb or add comment (Recommended)

5.3 — Use a distinct context message for toggles (Recommended)

5.4 — Fix docs or reorder annotations for consistency (Recommended)

5.5 — Document the origin and reason (Recommended)

5.6 — Introduce a GameType enum and use @Enumerated(EnumType.STRING) (Recommended)

6.1 — Use primitives where values are always present (Recommended)

6.2 — Return Optional<LocalDate> and update callers to handle empty (Recommended)

6.3 — Fix extraction logic to use tracked offsets or regex anchors (Recommended)

6.4 — Refactor to idiomatic flatMap chain (Recommended)

6.5 — Fix formatting and run code formatter (Recommended)

6.6 — Add import or use Map.of()/Map.copyOf() (Recommended)

6.7 — Replace with named constants or use ApplicationCommandOptionType.* (Recommended)

6.8 — Enforce a max player name length; keep PLAYER_COL_WIDTH = 15. Add a code comment explaining columns are fixed-width and longer names should be rejected or truncated (Recommended)

7.1 — Use Awaitility or polling assertions (Recommended)

7.2 — Inject via @Value or DiscordChannelProperties (Recommended)

7.3 — Use an encoding/escaping scheme or switch to JSON storage and add tests for delimiter collision (Recommended)

7.4 — Remove duplicate setup (Recommended)

8.1 — Restrict triggers to main and PR branches; run E2E only on main or manually via workflow_dispatch (Recommended)

8.2 — Use ${project.version} or evaluate project.version in CI (Recommended)

8.3 — Download and use the artifact produced by the build job (Recommended)

8.4 — Unify coverage thresholds across modules to 80% (Recommended)

8.5 — Align POM with CI key (wandocorp-tech_nyt-scorebot) or remove key from POM and set in CI (Recommended)

9.1 — SonarCloud Critical: Wildcard generic Mono<?> (MessageListener) — Fix to use Mono<Void> or concrete typed Monos (Recommended)

9.2 — SonarCloud Major: Thread.sleep() in EndToEndTest — Use Awaitility/polling assertions (Recommended)

9.* — SonarCloud Minor issues (see dashboard) — Address individually per Sonar report or batch cleanups (Recommended)

---

## 12 — Work batches

The audit findings have been grouped into ordered work batches to be completed sequentially. Each batch is recorded as a todo in the session database so work can be tracked and assigned.

1. batch-1-model-core — Data model & polymorphism
   - Includes: 1.1 (Normalize Scoreboard), 1.2 (Replace instanceof chains with polymorphism), 1.4 (Split CrosswordResult), 6.1 (Boxed primitives -> primitives), 5.6 (Introduce GameType enum)

2. batch-2-reactive-concurrency — Reactive correctness & concurrency
   - Includes: 3.1 (Remove blocking calls in beans), 3.2 (Add onError handlers for .subscribe()), 3.3 (Fix Mono<?> types), 3.4 (Fix ResultsChannelService race), 3.5 (Null-safe isFromConfiguredUser)

3. batch-3-config-ci-infra — Config, CI, and infra hardening
   - Includes: 2.1 (Replace ddl-auto=update with Flyway), 2.2 (Use managed Postgres/MySQL in prod), 2.3 (Move channel IDs to env/config), 2.4 (Add Actuator health/readiness), 2.6 (Graceful shutdown), 8.1 (Restrict CI triggers), 8.3 (Use build artifact in release), 8.2 (Use project.version in CI), 8.5 (Align Sonar project key)

4. batch-4-refactor-services-commands — Refactor services, listeners, and rendering
   - Includes: 4.1 (Decompose ScoreboardService.saveResult), 4.2 (Refactor ScoreboardRenderer), 4.3 (Split SlashCommandListener), 4.4 (Extract command registration), 4.5 (DRY ResultsChannelService), 1.5 (Move DiscordConfig to config package), 1.6 (Organize BotText constants), 5.1 (Rename User.discordUserId), 5.2 (Clarify renderer variable names)

5. batch-5-tests-sonar-cleanup — Tests, Sonar fixes, and misc cleanup
   - Includes: 7.1 (Replace Thread.sleep with Awaitility), 9.1 / 9.2 (Fix Sonar Critical/Major), 7.2 (Inject channel IDs into tests), 7.3 (Fix StringListConverter delimiter edge cases), 7.4 (Remove duplicate mock setup), 8.4 (Unify JaCoCo thresholds), plus remaining formatting, minor Sonar issues, and small cleanups (6.4, 6.5, 6.6, 6.7, 6.8, 5.4, 5.5, 6.3)

Todos created: batch-1-model-core, batch-2-reactive-concurrency, batch-3-config-ci-infra, batch-4-refactor-services-commands, batch-5-tests-sonar-cleanup

---

*After filling these in, reply here or commit directly to the repository and I will help turn decisions into todos.*
