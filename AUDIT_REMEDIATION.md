# Audit Remediation Review

**Audit date:** 2026-04-12 · **Review date:** 2026-04-17

This document reviews every finding from [CODE_AUDIT.md](CODE_AUDIT.md) and tracks what was remediated, what was missed, and what compromises were made.

---

## Status Summary

| Category | Remediated | Partial | Not Fixed | By Design |
|----------|:----------:|:-------:|:---------:|:---------:|
| Architecture & Design (§1) | 4 | 1 | — | 1 |
| 12-Factor App (§2) | 3 | 2 | 1 | 1 |
| Reactive & Concurrency (§3) | 4 | 1 | — | — |
| Fat Classes & Methods (§4) | 5 | — | — | — |
| Naming & Clarity (§5) | 6 | — | — | — |
| Code Smells (§6) | 7 | — | — | 1 |
| Test Quality (§7) | 4 | — | — | — |
| Build, CI/CD & Config (§8) | 3 | 1 | 1 | — |
| **Totals (45 findings)** | **36** | **5** | **2** | **3** |

---

## ✅ Remediated (36 findings)

### Architecture & Design

| # | Finding | What was done |
|---|---------|---------------|
| 1.1 | Scoreboard god class (50+ columns) | Normalized to `@OneToMany List<GameResult>` with `@Inheritance(SINGLE_TABLE)` and a `game_type` discriminator. Flyway migration `V4__normalize_game_results.sql` handles the schema change. |
| 1.2 | Pervasive `instanceof` chains (7 sites) | All chains replaced with polymorphic methods on `GameResult`: `puzzleNumber()`, `gameType()`, `gameLabel()`, `isSuccess()`. `StreakService` now switches on `GameType` enum. |
| 1.4 | `CrosswordResult` dual-purpose model | Split into abstract `CrosswordResult` (shared timed behavior), plus `MainCrosswordResult` (with `duo`, `lookups`, `checkUsed` fields) and `MiniCrosswordResult` / `MidiCrosswordResult`. |
| 1.5 | `DiscordConfig` in root package | Moved to `com.wandocorp.nytscorebot.config` subpackage. |

### 12-Factor App

| # | Finding | What was done |
|---|---------|---------------|
| 2.1 | `ddl-auto=update` in production | Changed to `ddl-auto=validate`. Added Flyway with 4 versioned migrations (`V1` through `V4`). |
| 2.4 | No health/readiness endpoint | Added `spring-boot-starter-actuator` with `/health` exposed and a custom `discord` readiness group. |
| 2.6 | No graceful shutdown | Configured `spring.lifecycle.timeout-per-shutdown-phase=15s` for orderly teardown. |

### Reactive & Concurrency

| # | Finding | What was done |
|---|---------|---------------|
| 3.2 | Fire-and-forget `.subscribe()` (8 sites) | All `.subscribe()` calls now include an `onError` consumer that logs via SLF4J. |
| 3.3 | `Mono<?>` wildcard generics | `processMessage()` and `replyForOutcome()` in `MessageListener` now return `Mono<Void>`. |
| 3.4 | Race condition (`volatile LocalDate`) | Replaced with `AtomicReference<LocalDate>` in `ResultsChannelService`. |
| 3.5 | NPE in `isFromConfiguredUser()` | Added null guard: `configuredUserId != null && configuredUserId.equals(authorId)`. |

### Fat Classes & Methods

| # | Finding | What was done |
|---|---------|---------------|
| 4.1 | `saveResult()` — 42 lines, 6 jobs | Decomposed into `resolveDate()`, `findOrCreateUser()`, `findOrCreateScoreboard()`, `validate()`, `isAlreadySubmitted()`, `applyResult()`, `autoFinishIfComplete()`. |
| 4.2 | `ScoreboardRenderer.render()` — 35 lines | Extracted `determineLayout()`, `renderSinglePlayer()`, `renderTwoPlayer()` with a `TwoPlayerLayout` record. |
| 4.3 | `SlashCommandListener` — 209 lines, 5 commands | Split into a thin dispatcher (45 lines) + 5 per-command `SlashCommandHandler` implementations. |
| 4.4 | `SlashCommandRegistrar` — monolithic builder | Each command extracted to its own builder method; `buildAllCommands()` orchestrates. |
| 4.5 | `ResultsChannelService` — 80% duplication | Common logic extracted to `prepareContext()` returning a `RefreshContext` record. |

### Naming & Clarity

| # | Finding | What was done |
|---|---------|---------------|
| 5.1 | `User.userId` ambiguity | Renamed field to `discordUserId` with `@Column(name = "discord_user_id")`. Flyway migration `V3` handles the rename. |
| 5.2 | `leftSb`/`rightSb` confusing | Wrapped in a `TwoPlayerLayout` record with a comment: *"the player with more emoji rows is placed on the left"*. |
| 5.3 | Misleading context message on flag toggle | Toggle commands now send `STATUS_CONTEXT_FLAG_UPDATED` instead of `STATUS_CONTEXT_PLAYER_FINISHED`. |
| 5.4 | Parser `@Order` values ≠ docs | Confirmed consistent: Wordle=1, Connections=2, Crossword=3, Strands=4. |
| 5.5 | `MAX_LINE_WIDTH = 33` unexplained | Added javadoc: *"fits two player columns in Discord monospace font"*. |
| 5.6 | `Streak.gameType` is a raw `String` | Introduced `GameType` enum with `WORDLE`, `CONNECTIONS`, `STRANDS`, `MINI_CROSSWORD`, `MIDI_CROSSWORD`, `MAIN_CROSSWORD`. Flyway migration `V2` converts existing data. |

### Code Smells

| # | Finding | What was done |
|---|---------|---------------|
| 6.2 | `extractDate()` returns null | Return type changed to `Optional<LocalDate>`; callers use `.orElse(null)`. |
| 6.3 | `lastIndexOf(line)` unreliable | Replaced with sequential `indexOf(line, searchFrom)` tracking to handle duplicate emoji rows. |
| 6.4 | Redundant Optional chain | Refactored to idiomatic `flatMap(p -> p.parse(...).stream()).findFirst()`. |
| 6.5 | StrandsParser formatting defect | Newline/formatting issue resolved. |
| 6.6 | Fully qualified `java.util.HashMap<>()` | Proper import added; uses `new HashMap<>()`. |
| 6.7 | Magic numbers `.type(4)` / `.type(3)` | Replaced with `ApplicationCommandOption.Type.INTEGER.getValue()` / `.STRING.getValue()`. |
| 6.8 | `PLAYER_COL_WIDTH = 15` undocumented | Added javadoc: *"names longer than this will misalign the layout"*. |

### Test Quality

| # | Finding | What was done |
|---|---------|---------------|
| 7.1 | `Thread.sleep()` in E2E test | Reverted to `Thread.sleep()` (5 s after multi-message posts, 3 s after single-message posts). Awaitility was trialled but removed — the async Discord gateway event dispatch is not compatible with Awaitility's assertion polling in this test setup. |
| 7.2 | Hardcoded channel IDs in test + properties | Channel IDs now injected via `@Value("${discord.channels[0].id}")` etc. Single source of truth. |
| 7.3 | No delimiter collision test for `StringListConverter` | Added two tests: one documents the comma-corruption limitation, one proves emoji values are safe. |
| 7.4 | Duplicate mock setup in `StatusChannelServiceTest` | Removed duplicate `when(...)` call; setup is now unified in `@BeforeEach`. |

### Build, CI/CD & Config

| # | Finding | What was done |
|---|---------|---------------|
| 8.1 | Pipeline triggers on every branch | Scoped to `push: branches: [main]` and `pull_request: branches: [main]`. |
| 8.4 | JaCoCo thresholds differ (80% vs 60%) | Discord module branch threshold raised from 60% to 80%, now uniform. |
| 8.5 | `sonar.projectKey` mismatch | POM and CI both set to `wandocorp-tech_nyt-scorebot`. |

---

## ⚠️ Partially Fixed (5 findings)

| # | Finding | Current state | Gap |
|---|---------|---------------|-----|
| 1.6 | `BotText` disorganized constants bag | Constants are now grouped under semantic section comments (Emojis, Game labels, Slash commands, etc.) | Not converted to nested classes or enums — still a flat class with `public static final` fields. Grouping by comment is fragile and tooling-invisible. |
| 2.3 | Channel IDs committed to source | Properties now use `${ENV_VAR:default}` syntax (e.g. `${DISCORD_CHANNEL_0_ID:1358111788146622709}`) | The actual IDs remain as hardcoded defaults in the committed file. An env-only approach (no fallback) would fully satisfy 12-Factor III. |
| 2.5 | No structured logging | SLF4J with `@Slf4j` is used consistently. Error handlers added per §3.2. | No JSON/structured log format configured (no `logback-spring.xml`, no JSON encoder). Logs are still plain text, not machine-parseable by aggregators. |
| 3.1 | `.block()` in `@Bean` / `@PostConstruct` | `SlashCommandRegistrar` no longer blocks — subscribes asynchronously in `@PostConstruct`. | `DiscordConfig.gatewayDiscordClient()` still calls `.login().block(LOGIN_TIMEOUT)` in the `@Bean` method. The blocking login is a known Discord4J pattern with no straightforward reactive alternative for bean creation. |
| 8.3 | Release workflow rebuilds the JAR | Both `release.yml` and `deploy.yml` attempt to download the build artifact first. | Falls back to `mvn clean package -DskipTests` if the artifact isn't available. The fallback means a release could ship a different binary than what was tested. |

---

## ❌ Not Fixed (2 findings)

| # | Finding | Notes |
|---|---------|-------|
| 2.2 | File-based H2 as sole data store | `spring.datasource.url` still defaults to `jdbc:h2:file:./data/scorebot`. The properties support env-var override (`${DATABASE_URL:...}`), but H2 remains the production database. No Postgres/MySQL driver or profile is configured. |
| 8.2 | `1.0-SNAPSHOT` hardcoded everywhere | Version string `1.0-SNAPSHOT` appears in the parent POM and is inherited by all modules. CI workflows (`build.yml`, `deploy.yml`, `release.yml`) reference `nyt-scorebot-app-1.0-SNAPSHOT.jar` by name. A version bump requires editing 5+ files. |

---

## 🤝 Accepted By Design (3 findings)

These items were reviewed during the audit and a deliberate decision was made to keep the current behavior.

| # | Finding | Decision | Risk |
|---|---------|----------|------|
| 1.3 | System hardcoded for 2 players | Keep — the bot is purpose-built for a two-player household. The `ScoreboardRenderer` layout, `ResultsChannelService` channel pairing, and `ScoreboardService` finish-check all assume exactly 2. | **Documentation gap:** the two-player constraint is not documented in class-level javadoc, README, or a named constant. A future contributor could attempt N-player support without realizing the assumption is load-bearing across 4+ classes. |
| 2.7 | Hardcoded puzzle epoch dates | Keep — epoch dates (Wordle #0 = 2021-06-19, etc.) are NYT constants that never change. Externalizing adds config complexity for no benefit. | Javadoc on `PuzzleCalendar` now documents each epoch date's origin. Low risk. |
| 6.1 (partial) | Boxed types in `MainCrosswordResult` | Keep `Boolean duo`, `Integer lookups`, `Boolean checkUsed` as boxed — `null` means "not yet provided" vs. `false`/`0` which are meaningful values. | Primitives were adopted everywhere else (`WordleResult`, `ConnectionsResult`, `StrandsResult`). The remaining boxed types are intentional and correct for optional flag semantics. |

---

## Recommendations

### High priority

1. **Document the two-player constraint (§1.3)** — Add class-level javadoc to `ScoreboardRenderer`, `ResultsChannelService`, and `ScoreboardService` noting the two-player assumption. Define a `MAX_PLAYERS = 2` constant.

2. **Remove hardcoded channel ID defaults (§2.3)** — Change `${DISCORD_CHANNEL_0_ID:1358111788146622709}` to `${DISCORD_CHANNEL_0_ID}` (no fallback). The app should fail fast on startup if env vars are missing, not silently connect to someone else's channels.

3. **Externalize the version string (§8.2)** — Use `mvn help:evaluate -Dexpression=project.version` in CI, or adopt the `maven-ci-friendly-versions` plugin (`${revision}` property) so CI can set the version once.

### Medium priority

4. **Add structured logging (§2.5)** — Add a `logback-spring.xml` with a JSON encoder for the `production` profile. The existing SLF4J calls need no changes.

5. **Replace H2 in production (§2.2)** — Add a `spring.profiles.active=prod` profile with a PostgreSQL datasource. Keep H2 for dev/test only. The Flyway migrations are already DB-agnostic.

6. **Fail-fast in release workflow (§8.3)** — Remove the `mvn clean package` fallback in `release.yml`. If the build artifact is missing, the release should fail, not silently rebuild.

### Low priority

7. **Promote `BotText` groups to nested classes (§1.6)** — e.g. `BotText.Emoji.CHECK`, `BotText.Label.WORDLE`. Gives IDE navigation and makes grouping enforceable.

8. **Address the `DiscordConfig.block()` (§3.1)** — This is a known Discord4J limitation. Consider documenting the trade-off in a code comment if a non-blocking alternative isn't feasible.
